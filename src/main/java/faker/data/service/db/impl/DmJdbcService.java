package faker.data.service.db.impl;

import faker.data.domain.DataSource;
import faker.data.service.db.AbstractJdbcService;

public class DmJdbcService extends AbstractJdbcService {
	
	public DmJdbcService(DataSource dataSource) {
		super(dataSource);
	}
	
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