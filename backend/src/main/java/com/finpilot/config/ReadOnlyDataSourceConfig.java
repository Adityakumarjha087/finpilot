package com.finpilot.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two connection pools: the primary (read/write, owns the schema) and a tiny read-only pool that logs in
 * as the restricted {@code finpilot_readonly} role used exclusively by the text-to-SQL assistant.
 */
@Configuration
public class ReadOnlyDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(destroyMethod = "close")
    @Qualifier("readonly")
    HikariDataSource readonlyDataSource(AppProperties props) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("readonly");
        cfg.setJdbcUrl(props.readonlyDb().url());
        cfg.setUsername(props.readonlyDb().username());
        cfg.setPassword(props.readonlyDb().password());
        cfg.setMaximumPoolSize(3);
        cfg.setReadOnly(true);
        cfg.setAutoCommit(false);
        cfg.setInitializationFailTimeout(-1); // role is created by Flyway; don't fail fast before migration
        return new HikariDataSource(cfg);
    }

    @Bean
    @Qualifier("readonly")
    JdbcTemplate readonlyJdbcTemplate(@Qualifier("readonly") HikariDataSource ds) {
        JdbcTemplate t = new JdbcTemplate(ds);
        t.setQueryTimeout(5);
        t.setMaxRows(500);
        return t;
    }

    @Bean
    @Qualifier("readonly")
    TransactionTemplate readonlyTransactionTemplate(@Qualifier("readonly") HikariDataSource ds) {
        TransactionTemplate tt = new TransactionTemplate(new DataSourceTransactionManager(ds));
        tt.setReadOnly(true);
        tt.setTimeout(6);
        return tt;
    }
}
