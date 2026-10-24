package faker.data.service.db.impl;

import faker.data.domain.DataSource;
import faker.data.service.db.AbstractJdbcService;
import faker.data.service.generate.engine.GenerationEngine;

public class OracleJdbcService extends AbstractJdbcService {
	
	public OracleJdbcService(DataSource dataSource) {
		super(dataSource);
	}

    public OracleJdbcService(DataSource dataSource, GenerationEngine engine) { super(dataSource, engine); }
	
	@Override
	protected String loadDriverClass() {
		return "oracle.jdbc.driver.OracleDriver";
	}
	
	/**
	 * 注意：oracle用户名称须大写
	 */
	@Override
	protected String schemaPattern() {
		//oracle的schemaPattern为用户名
		return getDataSource().getUsername().toUpperCase();
	}
}
