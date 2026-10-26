-- Run ONLY in a dedicated empty test schema. No DROP/DELETE statements.
CREATE TABLE demo_customers(id VARCHAR(36) PRIMARY KEY,display_name VARCHAR(64),enabled INT);
INSERT INTO demo_customers VALUES('customer-1','Alice',1),('customer-2','Bob',1),('customer-3','Inactive',0);
CREATE TABLE demo_orders(id VARCHAR(36) PRIMARY KEY,customer_id VARCHAR(36) NOT NULL,customer_name VARCHAR(64),state VARCHAR(16) NOT NULL,created_at TIMESTAMP,FOREIGN KEY(customer_id) REFERENCES demo_customers(id));
CREATE TABLE demo_order_items(id VARCHAR(36) PRIMARY KEY,order_id VARCHAR(36) NOT NULL,quantity INT NOT NULL,unit_price DECIMAL(12,2),FOREIGN KEY(order_id) REFERENCES demo_orders(id));
CREATE TABLE demo_order_contents(id VARCHAR(36) PRIMARY KEY,order_id VARCHAR(36) NOT NULL,role VARCHAR(16) NOT NULL,payload BLOB,FOREIGN KEY(order_id) REFERENCES demo_orders(id));
CREATE TABLE demo_order_tasks(id VARCHAR(36) PRIMARY KEY,order_id VARCHAR(36) NOT NULL,input_content_id VARCHAR(36),output_content_id VARCHAR(36),FOREIGN KEY(order_id) REFERENCES demo_orders(id),FOREIGN KEY(input_content_id) REFERENCES demo_order_contents(id),FOREIGN KEY(output_content_id) REFERENCES demo_order_contents(id));
