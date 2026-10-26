package com.taehyung.mes.routing;

import com.taehyung.mes.common.actor.EnvironmentActorProvider;
import com.taehyung.mes.item.ItemMapper;
import com.taehyung.mes.process.ProcessMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 명시적 opt-in 전용. 일반 DB_* 환경변수와 application.yml을 사용하지 않는다. */
@EnabledIfEnvironmentVariable(named = "MES_ROUTING_MYSQL_TEST", matches = "LOCAL_TEST_ONLY")
class RoutingMysqlIntegrationTests {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private RoutingMapper mapper;
    private ItemMapper items;
    private ProcessMapper processes;
    private RoutingService service;
    private String actor;
    private Long productId;
    private Long processId;

    @BeforeEach
    void setUp() throws Exception {
        String database = requiredEnv("ROUTING_TEST_DB_NAME");
        if (!database.matches("[a-z0-9_]+_routing_test")) { throw new IllegalArgumentException("Use a dedicated *_routing_test schema."); }
        int port = Integer.parseInt(System.getenv().getOrDefault("ROUTING_TEST_DB_PORT", "3306"));
        if (port < 1 || port > 65535) { throw new IllegalArgumentException("Invalid local test port."); }
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:" + port + "/" + database + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",
                requiredEnv("ROUTING_TEST_DB_USER"), requiredEnv("ROUTING_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(database);
        transactions = new DataSourceTransactionManager(source); transactions.setDefaultTimeout(10);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
        Configuration configuration = new Configuration(); configuration.setMapUnderscoreToCamelCase(true); factory.setConfiguration(configuration);
        var resolver = new PathMatchingResourcePatternResolver();
        factory.setMapperLocations(resolver.getResource("classpath:mappers/routing/RoutingMapper.xml"),
                resolver.getResource("classpath:mappers/item/ItemMapper.xml"), resolver.getResource("classpath:mappers/process/ProcessMapper.xml"));
        factory.afterPropertiesSet();
        SqlSessionTemplate sessions = new SqlSessionTemplate(factory.getObject());
        mapper = sessions.getMapper(RoutingMapper.class); items = sessions.getMapper(ItemMapper.class); processes = sessions.getMapper(ProcessMapper.class);
        actor = "ROUTING_IT_" + UUID.randomUUID();
        jdbc.update("INSERT INTO item(item_code,item_name,item_kind,unit_code,is_active,created_by) VALUES (?,?,'FINISHED','EA',1,?)", actor, "통합 제품", actor);
        productId = jdbc.queryForObject("SELECT item_id FROM item WHERE item_code=?", Long.class, actor);
        jdbc.update("INSERT INTO manufacturing_process(process_code,process_name,is_active,created_by) VALUES (?, ?,1,?)", actor, "원래 공정", actor);
        processId = jdbc.queryForObject("SELECT process_id FROM manufacturing_process WHERE process_code=?", Long.class, actor);
        service = transactional(mapper);
    }

    @AfterEach
    void cleanOwnFixtures() {
        if (actor == null) return;
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM routing_step WHERE created_by=?", actor);
            jdbc.update("DELETE FROM routing_revision WHERE created_by=?", actor);
            jdbc.update("DELETE FROM routing WHERE created_by=?", actor);
            jdbc.update("DELETE FROM manufacturing_process WHERE created_by=?", actor);
            jdbc.update("DELETE FROM item WHERE created_by=?", actor);
        });
    }

    @Test
    void mysqlPersistsSnapshotsAndKeepsOldRevisionAfterProcessRename() {
        Long oldId = service.create(form(1L));
        jdbc.update("UPDATE manufacturing_process SET process_name=? WHERE process_id=?", "변경된 공정", processId);
        RoutingRevision old = service.get(oldId);
        assertThat(old.getSteps().get(0).getProcessNameSnapshot()).isEqualTo("원래 공정");
        Long newId = service.addRevision(old.getRoutingId(), form(2L));
        assertThat(service.get(newId).getSteps().get(0).getProcessNameSnapshot()).isEqualTo("변경된 공정");
        assertThat(service.get(oldId).getSteps().get(0).getProcessNameSnapshot()).isEqualTo("원래 공정");
    }

    @Test
    void mysqlRollsBackHeaderRevisionAndEarlierStepAfterLaterFailure() {
        RoutingMapper failing = spy(mapper);
        doAnswer(call -> {
            RoutingStep row = call.getArgument(0);
            if (row.getStepNo() == 20L) { throw new DataIntegrityViolationException("injected later step failure"); }
            return mapper.insertStep(row);
        }).when(failing).insertStep(any());
        RoutingForm form = form(1L); RoutingStepForm second = new RoutingStepForm(); second.setProcessId(processId); second.setStepNo(20L); form.getSteps().add(second);
        assertThatThrownBy(() -> transactional(failing).create(form)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routing WHERE created_by=?", Long.class, actor)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routing_revision WHERE created_by=?", Long.class, actor)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routing_step WHERE created_by=?", Long.class, actor)).isZero();
    }

    @Test
    void actualUniqueViolationAfterStalePrecheckBecomesBusinessError() {
        service.create(form(1L));
        RoutingMapper stale = spy(mapper); doReturn(false).when(stale).existsByCode(actor);
        assertThatThrownBy(() -> transactional(stale).create(form(1L))).isInstanceOf(RoutingRuleException.class).hasMessageContaining("중복");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routing WHERE routing_code=?", Long.class, actor)).isEqualTo(1L);
    }

    @Test
    void concurrentRegistrationStoresOnlyOneRouting() throws Exception {
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Boolean> task = () -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                try { service.create(form(1L)); return true; } catch (RoutingRuleException duplicate) { return false; }
            };
            var first = pool.submit(task); var second = pool.submit(task); start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routing WHERE routing_code=?", Long.class, actor)).isEqualTo(1L);
        } finally {
            start.countDown(); pool.shutdownNow();
            if (!pool.awaitTermination(12, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test threads did not stop.");
        }
    }

    @Test
    void processLockWaitsForDeactivationThenRejectsNewComposition() throws Exception {
        var pool = Executors.newFixedThreadPool(2); var locked = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        try {
            var holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                processes.findByIdForUpdate(processId); locked.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout");
                } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
                jdbc.update("UPDATE manufacturing_process SET is_active=0 WHERE process_id=?", processId);
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var writer = pool.submit(() -> { started.countDown(); return service.create(form(1L)); });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> writer.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); holder.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> writer.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(RoutingRuleException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routing WHERE created_by=?", Long.class, actor)).isZero();
        } finally {
            release.countDown(); pool.shutdownNow();
            if (!pool.awaitTermination(12, TimeUnit.SECONDS)) throw new IllegalStateException("Lock test threads did not stop.");
        }
    }

    private RoutingService transactional(RoutingMapper selected) {
        var target = new RoutingService(selected, items, processes, new EnvironmentActorProvider(actor));
        ProxyFactory proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (RoutingService) proxy.getProxy();
    }
    private RoutingForm form(Long revision) {
        RoutingForm form = new RoutingForm(); form.setRoutingCode(actor); form.setProductItemId(productId); form.setRevisionNo(revision);
        RoutingStepForm row = new RoutingStepForm(); row.setProcessId(processId); row.setStepNo(10L); form.getSteps().add(row); return form;
    }
    private String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing dedicated test variable: " + name);
        return value;
    }
}
