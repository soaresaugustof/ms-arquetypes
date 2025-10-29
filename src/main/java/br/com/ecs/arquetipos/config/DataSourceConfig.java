package br.com.ecs.arquetipos.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(Environment env) {
        // Prioriza JDBC_DATABASE_URL (já no formato jdbc:postgresql://)
        String jdbcDatabaseUrl = env.getProperty("JDBC_DATABASE_URL");
        if (jdbcDatabaseUrl != null && !jdbcDatabaseUrl.isBlank()) {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(jdbcDatabaseUrl);
            ds.setUsername(env.getProperty("spring.datasource.username"));
            ds.setPassword(env.getProperty("spring.datasource.password"));
            return ds;
        }

        // Em muitos provedores (Railway/Heroku) a variável vem como DATABASE_URL no formato
        // postgres://user:pass@host:port/dbname — convertemos para jdbc:postgresql://host:port/dbname
        String databaseUrl = env.getProperty("DATABASE_URL");
        if ((databaseUrl == null || databaseUrl.isBlank())) {
            databaseUrl = System.getenv("DATABASE_URL");
        }

        if (databaseUrl != null && !databaseUrl.isBlank()) {
            try {
                URI uri = new URI(databaseUrl);
                String userInfo = uri.getUserInfo();
                String username = null;
                String password = null;
                if (userInfo != null) {
                    String[] parts = userInfo.split(":", 2);
                    username = parts[0];
                    if (parts.length > 1) password = parts[1];
                }
                String host = uri.getHost();
                int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                String path = uri.getPath();
                if (path != null && path.startsWith("/")) path = path.substring(1);

                String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s?sslmode=require", host, port, path);

                HikariDataSource ds = new HikariDataSource();
                ds.setJdbcUrl(jdbcUrl);
                if (username != null) ds.setUsername(username);
                if (password != null) ds.setPassword(password);
                // pool defaults are OK for small apps, limit to reasonable number
                ds.setMaximumPoolSize(10);
                return ds;
            } catch (Exception ignored) {
                // se falhar, fallback para properties
            }
        }

        // Fallback para propriedades tradicionais (desenvolvimento local)
        String configuredUrl = env.getProperty("spring.datasource.url");
        HikariDataSource ds = new HikariDataSource();
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            ds.setJdbcUrl(configuredUrl);
        }
        ds.setUsername(env.getProperty("spring.datasource.username"));
        ds.setPassword(env.getProperty("spring.datasource.password"));
        return ds;
    }
}

