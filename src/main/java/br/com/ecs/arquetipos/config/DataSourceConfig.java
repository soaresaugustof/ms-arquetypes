package br.com.ecs.arquetipos.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(Environment env) {
        // 1) Prioriza JDBC_DATABASE_URL (já no formato jdbc:postgresql://)
        String jdbcDatabaseUrl = firstNonBlank(
                System.getenv("JDBC_DATABASE_URL"),
                env.getProperty("JDBC_DATABASE_URL")
        );
        if (jdbcDatabaseUrl != null && !jdbcDatabaseUrl.isBlank()) {
            log.info("Using JDBC_DATABASE_URL from environment");
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(jdbcDatabaseUrl);
            ds.setUsername(firstNonBlank(System.getenv("PGUSER"), env.getProperty("spring.datasource.username")));
            ds.setPassword(firstNonBlank(System.getenv("PGPASSWORD"), env.getProperty("spring.datasource.password")));
            ds.setMaximumPoolSize(10);
            return ds;
        }

        // 2) DATABASE_URL (formato postgresql://user:pass@host:port/db ou postgres://...)
        String databaseUrl = firstNonBlank(
                System.getenv("DATABASE_URL"),
                env.getProperty("DATABASE_URL")
        );

        if (databaseUrl != null && !databaseUrl.isBlank()) {
            log.info("Found DATABASE_URL in environment, converting to JDBC URL");
            try {
                // Já está no formato jdbc:...?
                if (databaseUrl.startsWith("jdbc:")) {
                    HikariDataSource ds = new HikariDataSource();
                    ds.setJdbcUrl(databaseUrl);
                    ds.setUsername(firstNonBlank(System.getenv("PGUSER"), env.getProperty("spring.datasource.username")));
                    ds.setPassword(firstNonBlank(System.getenv("PGPASSWORD"), env.getProperty("spring.datasource.password")));
                    ds.setMaximumPoolSize(10);
                    return ds;
                }

                URI uri = new URI(databaseUrl);
                String scheme = uri.getScheme();
                if (scheme == null) throw new IllegalArgumentException("Invalid DATABASE_URL: no scheme");
                // suportar postgres e postgresql
                if (scheme.startsWith("postgres")) {
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
                    else ds.setUsername(firstNonBlank(System.getenv("PGUSER"), env.getProperty("spring.datasource.username")));
                    if (password != null) ds.setPassword(password);
                    else ds.setPassword(firstNonBlank(System.getenv("PGPASSWORD"), env.getProperty("spring.datasource.password")));
                    ds.setMaximumPoolSize(10);
                    return ds;
                }
                // se esquema não é postgres, tenta usar como JDBC direto
            } catch (Exception e) {
                log.warn("Failed to parse DATABASE_URL: {}", e.getMessage());
            }
        }

        // 3) SPRING_DATASOURCE_URL / spring.datasource.url (fallbacks)
        String configuredUrl = firstNonBlank(
                System.getenv("SPRING_DATASOURCE_URL"),
                env.getProperty("spring.datasource.url"),
                env.getProperty("SPRING_DATASOURCE_URL")
        );
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            log.info("Using configured datasource URL from spring.datasource.url");
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(configuredUrl);
            ds.setUsername(firstNonBlank(System.getenv("PGUSER"), env.getProperty("spring.datasource.username")));
            ds.setPassword(firstNonBlank(System.getenv("PGPASSWORD"), env.getProperty("spring.datasource.password")));
            ds.setMaximumPoolSize(10);
            return ds;
        }

        // 4) Fallback para variáveis PG individuais (Railway fornece PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD)
        String pgHost = firstNonBlank(System.getenv("PGHOST"), env.getProperty("PGHOST"), System.getenv("POSTGRES_HOST"), env.getProperty("POSTGRES_HOST"));
        String pgPort = firstNonBlank(System.getenv("PGPORT"), env.getProperty("PGPORT"), System.getenv("POSTGRES_PORT"), env.getProperty("POSTGRES_PORT"));
        String pgDatabase = firstNonBlank(System.getenv("PGDATABASE"), env.getProperty("PGDATABASE"), System.getenv("POSTGRES_DB"), env.getProperty("POSTGRES_DB"));
        String pgUser = firstNonBlank(System.getenv("PGUSER"), env.getProperty("PGUSER"), System.getenv("POSTGRES_USER"), env.getProperty("POSTGRES_USER"));
        String pgPassword = firstNonBlank(System.getenv("PGPASSWORD"), env.getProperty("PGPASSWORD"), System.getenv("POSTGRES_PASSWORD"), env.getProperty("POSTGRES_PASSWORD"));

        if (pgHost != null && !pgHost.isBlank() && pgDatabase != null && !pgDatabase.isBlank()) {
            int port = 5432;
            try { if (pgPort != null && !pgPort.isBlank()) port = Integer.parseInt(pgPort); } catch (NumberFormatException ignored) {}
            String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s?sslmode=require", pgHost, port, pgDatabase);
            log.info("Constructed JDBC URL from PGHOST/PGPORT/PGDATABASE");
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(jdbcUrl);
            if (pgUser != null) ds.setUsername(pgUser);
            if (pgPassword != null) ds.setPassword(pgPassword);
            ds.setMaximumPoolSize(10);
            return ds;
        }

        // Se chegamos aqui, não encontramos URL válida
        String msg = "No database URL found. Set JDBC_DATABASE_URL, DATABASE_URL, spring.datasource.url, or provide PGHOST/PGDATABASE environment variables.";
        log.error(msg);
        throw new IllegalStateException(msg);
    }

    private String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
