package com.taehyung.mes.production;

import com.taehyung.mes.common.actor.EnvironmentActorProvider;
import com.taehyung.mes.item.ItemMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.sql.SQLException;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 전용 로컬 테스트 DB opt-in. 일반 DB_* 및 application.yml로 연결하지 않는다. */
@EnabledIfEnvironmentVariable(named = "MES_PLAN_MYSQL_TEST", matches = "LOCAL_TEST_ONLY")
class ProductionPlanMysqlIntegrationTests {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private ProductionPlanMapper mapper;
    private ItemMapper items;
    private ProductionPlanService service;
    private String actor;
    private Long productId;

    @BeforeEach
    void setUp() throws Exception {
        String database = requiredEnv("PLAN_TEST_DB_NAME");
        if (!database.matches("[a-z0-9_]+_plan_test")) throw new IllegalArgumentException("Use a dedicated *_plan_test schema.");
        int port = Integer.parseInt(System.getenv().getOrDefault("PLAN_TEST_DB_PORT", "3306"));
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid local test port.");
        var source = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true", requiredEnv("PLAN_TEST_DB_USER"), requiredEnv("PLAN_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(database);
        transactions = new DataSourceTransactionManager(source); transactions.setDefaultTimeout(10);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
        var configuration = new Configuration(); configuration.setMapUnderscoreToCamelCase(true); factory.setConfiguration(configuration);
        var resolver = new PathMatchingResourcePatternResolver();
        factory.setMapperLocations(resolver.getResource("classpath:mappers/production/ProductionPlanMapper.xml"), resolver.getResource("classpath:mappers/item/ItemMapper.xml"));
        factory.afterPropertiesSet(); var sessions = new SqlSessionTemplate(factory.getObject());
        mapper = sessions.getMapper(ProductionPlanMapper.class); items = sessions.getMapper(ItemMapper.class);
        actor = "PLAN_IT_" + UUID.randomUUID();
        jdbc.update("INSERT INTO item(item_code,item_name,item_kind,unit_code,is_active,created_by) VALUES (?,?,'FINISHED','EA',1,?)", actor, "계획 통합 제품", actor);
        productId = jdbc.queryForObject("SELECT item_id FROM item WHERE item_code=?", Long.class, actor);
        service = transactional(mapper);
    }

    @AfterEach
    void cleanOwnFixtures() {
        if (actor == null) return;
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM production_plan WHERE created_by=?", actor);
            jdbc.update("DELETE FROM item WHERE created_by=?", actor);
        });
    }

    @Test
    void mysqlPersistsAndSearchesPlanWithoutCreatingWorkOrders() {
        Long id = service.create(form());
        ProductionPlan stored = service.get(id);
        assertThat(stored.getPlannedQty()).isEqualByComparingTo("100");
        assertThat(stored.getPlannedDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(stored.getWorkOrderCount()).isZero();
        assertThat(service.list(id.toString(), actor, "계획 통합")).hasSize(1);
    }

    @Test
    void realMysqlConstraintFailureAfterInsertRollsBackTheInsertedPlan() {
        ProductionPlanMapper failing = spy(mapper);
        doAnswer(call -> {
            ProductionPlan row = call.getArgument(0);
            mapper.insert(row);
            // 전용 fixture에만 잘못된 수량을 시도해 실제 CHECK 위반 후 rollback을 확인한다.
            jdbc.update("UPDATE production_plan SET planned_qty=-1 WHERE production_plan_id=?", row.getProductionPlanId());
            return 1;
        }).when(failing).insert(any());
        Throwable failure = catchThrowable(() -> transactional(failing).create(form()));
        assertThat(failure).isInstanceOf(DataAccessException.class);
        // CHECK 3819/HY000은 번역기 설정에 따라 UncategorizedSQLException이 될 수 있다.
        Throwable sqlCause = failure;
        while (sqlCause != null && !(sqlCause instanceof SQLException)) { sqlCause = sqlCause.getCause(); }
        assertThat(sqlCause).isInstanceOf(SQLException.class);
        assertThat(((SQLException) sqlCause).getErrorCode()).isEqualTo(3819);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM production_plan WHERE created_by=?", Long.class, actor)).isZero();
    }

    @Test
    void itemLockWaitsForDeactivationAndThenRejectsNewPlan() throws Exception {
        var pool = Executors.newFixedThreadPool(2); var locked = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        try {
            var holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                items.findByIdForUpdate(productId); locked.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout");
                } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
                jdbc.update("UPDATE item SET is_active=0 WHERE item_id=?", productId);
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var writer = pool.submit(() -> { started.countDown(); return service.create(form()); });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> writer.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); holder.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> writer.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(ProductionPlanRuleException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM production_plan WHERE created_by=?", Long.class, actor)).isZero();
        } finally {
            release.countDown(); pool.shutdownNow();
            if (!pool.awaitTermination(12, TimeUnit.SECONDS)) throw new IllegalStateException("Test threads did not stop.");
        }
    }

    private ProductionPlanService transactional(ProductionPlanMapper selected) {
        var target = new ProductionPlanService(selected, items, new EnvironmentActorProvider(actor));
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (ProductionPlanService) proxy.getProxy();
    }
    private ProductionPlanForm form() {
        var form = new ProductionPlanForm(); form.setProductItemId(productId); form.setPlannedQty(new BigDecimal("100")); form.setPlannedDate(LocalDate.of(2026, 11, 1)); return form;
    }
    private String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing dedicated test variable: " + name);
        return value;
    }
}
