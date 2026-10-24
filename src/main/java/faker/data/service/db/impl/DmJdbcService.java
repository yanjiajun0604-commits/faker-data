package faker.data.service.db.impl;

import faker.data.domain.DataSource;
import faker.data.service.db.AbstractJdbcService;
import faker.data.service.generate.engine.GenerationEngine;

public class DmJdbcService extends AbstractJdbcService {
	
	public DmJdbcService(DataSource dataSource) {
		super(dataSource);
	}

    public DmJdbcService(DataSource dataSource, GenerationEngine engine) { super(dataSource, engine); }
	
	@Override
	protected String loadDriverClass() {
		return "dm.jdbc.driver.DmDriver";
	}
	
	@Override
	protected String schemaPattern() {
		//oracle的schemaPattern为用户名
		return getDataSource().getUsername().toUpperCase();
	}
}
