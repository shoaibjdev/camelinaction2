package camelinaction;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

public class SpringPropagationTest extends CamelSpringTestSupport {

    private JdbcTemplate jdbc;

    @Before
    public void setupDatabase() throws Exception {
        DataSource ds = context.getRegistry().lookupByNameAndType("myDataSource", DataSource.class);
        jdbc = new JdbcTemplate(ds);

        jdbc.execute("create table bookorders "
                + "( order_id varchar(10), order_book varchar(50) )");
        jdbc.execute("create table booktap "
                + "( order_id varchar(10), order_book varchar(50), order_redelivery varchar(5) )");
    }

    @After
    public void dropDatabase() throws Exception {
        jdbc.execute("drop table bookorders");
        jdbc.execute("drop table booktap");
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("SpringPropagationTest.xml");
    }

    @Test
    public void testWithCamel() throws Exception {
        // there should be 0 row in the database when we start
        assertEquals(Long.valueOf(0), jdbc.queryForObject("select count(*) from bookorders", Long.class));
        assertEquals(Long.valueOf(0), jdbc.queryForObject("select count(*) from booktap", Long.class));

        template.sendBody("activemq:queue:inbox", "Camel in Action");

        String reply = consumer.receiveBody("activemq:queue:order", 10000, String.class);
        assertEquals("Camel in Action", reply);

        // wait for the route to complete with success
        Thread.sleep(1000);

        // there should be 1 row in the database with the order
        assertEquals(Long.valueOf(1), jdbc.queryForObject("select count(*) from bookorders", Long.class));
        assertEquals(Long.valueOf(1), jdbc.queryForObject("select count(*) from booktap", Long.class));

        // print the SQL
        log.info("The following orders was recorded in the orders ...");
        List<Map<String, Object>> rows = jdbc.queryForList("select * from bookorders");
        for (Map<String, Object> row : rows) {
            log.info("Book order[id={}, book={}]", row.get("order_id"), row.get("order_book"));
        }
        log.info("The following orders was recorded in the wiretap ...");
        rows = jdbc.queryForList("select * from booktap");
        for (Map<String, Object> row : rows) {
            log.info("Book wire tap[id={}, book={}, redelivery={}]", row.get("order_id"), row.get("order_book"), row.get("order_redelivery"));
        }
    }

    @Test
    public void testWithDonkey() throws Exception {
        // there should be 0 row in the database when we start
        assertEquals(Long.valueOf(0), jdbc.queryForObject("select count(*) from bookorders", Long.class));

        template.sendBody("activemq:queue:inbox", "Donkey in Action");

        String reply = consumer.receiveBody("activemq:queue:order", 10000, String.class);
        assertNull("There should be no reply", reply);

        reply = consumer.receiveBody("activemq:queue:ActiveMQ.DLQ", 10000, String.class);
        assertNotNull("It should have been moved to DLQ", reply);

        // wait for the route to complete with success
        Thread.sleep(1000);

        // there should be 0 row in the database with the order
        assertEquals(Long.valueOf(0), jdbc.queryForObject("select count(*) from bookorders", Long.class));
        // there should be 1 + 6 redelivery attempt row in the database with the wire tap
        assertEquals(Long.valueOf(1), jdbc.queryForObject("select count(*) from booktap where order_redelivery = 'false'", Long.class));
        assertEquals(Long.valueOf(6), jdbc.queryForObject("select count(*) from booktap where order_redelivery = 'true'", Long.class));

        // print the SQL
        log.info("The following orders was recorded in the orders ...");
        List<Map<String, Object>> rows = jdbc.queryForList("select * from bookorders");
        for (Map<String, Object> row : rows) {
            log.info("Book order[id={}, book={}]", row.get("order_id"), row.get("order_book"));
        }
        log.info("The following orders was recorded in the wiretap ...");
        rows = jdbc.queryForList("select * from booktap");
        for (Map<String, Object> row : rows) {
            log.info("Book wire tap[id={}, book={}, redelivery={}]", row.get("order_id"), row.get("order_book"), row.get("order_redelivery"));
        }
    }

}
