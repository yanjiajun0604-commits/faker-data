package faker.data.service.db.impl;

import faker.data.domain.DataSource;
import faker.data.service.db.AbstractJdbcService;

public class MySQLJdbcService extends AbstractJdbcService {

	public MySQLJdbcService(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	protected String loadDriverClass() {
		// MySQL Connector/J 8.x 驱动类从 com.mysql.jdbc.Driver 迁移到 com.mysql.cj.jdbc.Driver
		return "com.mysql.cj.jdbc.Driver";
	}

	@Override
	protected String catalog() {
		// MySQL 中 catalog 才是过滤数据库（database）的真正参数；
		// 见 MySQLJdbcServiceTest.catalog_* 用例
		return parseDatabaseName(getDataSource().getUrl());
	}

	@Override
	protected String schemaPattern() {
		// MySQL 没有 schema 概念；若返回非空，Connector/J 8.x 会忽略该过滤并返回所有库的所有表
		return null;
	}

	/**
	 * 从 JDBC URL 解析出数据库名。
	 * 例如：jdbc:mysql://localhost:3306/fakertest?useSSL=false -> fakertest
	 * 当 URL 中无 /db 段或 db 为空时返回 null，避免把空串传给 meta.getTables 触发误过滤。
	 */
	static String parseDatabaseName(String url) {
		if (url == null) {
			return null;
		}
		int slashIdx = url.indexOf("/", "jdbc:mysql:".length() + 2);
		if (slashIdx < 0) {
			return null;
		}
		String database = url.substring(slashIdx + 1);
		int qIdx = database.indexOf("?");
		if (qIdx >= 0) {
			database = database.substring(0, qIdx);
		}
		return database.isEmpty() ? null : database;
	}
}