package org.jdbcdslog;

import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.PooledConnection;
import javax.sql.XAConnection;
import javax.sql.XADataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionPoolXADataSourceProxy implements DataSource, XADataSource, ConnectionPoolDataSource
	, Serializable {

	static Logger logger = LoggerFactory.getLogger(ConnectionPoolXADataSourceProxy.class);
	
	static final String targetDSParameter = "targetDS";
	
	Object targetDS = null;
	
	Map props = new HashMap();

	public ConnectionPoolXADataSourceProxy() throws JDBCDSLogException {
	}

	public Connection getConnection() throws SQLException {
		logger.info("getConnection()");
		if(targetDS == null)
			throw new SQLException("targetDS parameter has not been passed to Database or URL property.");
		if(targetDS instanceof DataSource)
			return ConnectionLoggingProxy.wrap(((DataSource)targetDS).getConnection());
		else 
			throw new SQLException("targetDS doesn't implement DataSource interface.");
	}

	public Connection getConnection(String username, String password)
			throws SQLException {
		if(targetDS instanceof DataSource)
			return ConnectionLoggingProxy.wrap(((DataSource)targetDS).getConnection(username, password));
		else
			throw new SQLException("targetDS doesn't implement DataSource interface.");
	}

	public PrintWriter getLogWriter() throws SQLException {
		if(targetDS instanceof DataSource)
			return ((DataSource)targetDS).getLogWriter();
		if(targetDS instanceof XADataSource)
			return ((XADataSource)targetDS).getLogWriter();
		if(targetDS instanceof ConnectionPoolDataSource)
			return ((ConnectionPoolDataSource)targetDS).getLogWriter();
		throw new SQLException("targetDS doesn't have getLogWriter() method");
	}

	public int getLoginTimeout() throws SQLException {
		if(targetDS instanceof DataSource)
			return ((DataSource)targetDS).getLoginTimeout();
		if(targetDS instanceof XADataSource)
			return ((XADataSource)targetDS).getLoginTimeout();
		if(targetDS instanceof ConnectionPoolDataSource)
			return ((ConnectionPoolDataSource)targetDS).getLoginTimeout();
		throw new SQLException("targetDS doesn't have getLogTimeout() method");
	}

	public void setLogWriter(PrintWriter out) throws SQLException {
		if(targetDS instanceof DataSource)
			((DataSource)targetDS).setLogWriter(out);
		if(targetDS instanceof XADataSource)
			((XADataSource)targetDS).setLogWriter(out);
		if(targetDS instanceof ConnectionPoolDataSource)
			((ConnectionPoolDataSource)targetDS).setLogWriter(out);
		throw new SQLException("targetDS doesn't have setLogWriter() method");
	}

	public void setLoginTimeout(int seconds) throws SQLException {
		if(targetDS instanceof DataSource)
			((DataSource)targetDS).setLoginTimeout(seconds);
		if(targetDS instanceof XADataSource)
			((XADataSource)targetDS).setLoginTimeout(seconds);
		if(targetDS instanceof ConnectionPoolDataSource)
			((ConnectionPoolDataSource)targetDS).setLoginTimeout(seconds);
		throw new SQLException("targetDS doesn't have setLogWriter() method");
	}

	public XAConnection getXAConnection() throws SQLException {
		if(targetDS instanceof XADataSource)
			return XAConnectionLoggingProxy.wrap(((XADataSource)targetDS).getXAConnection());
		else
			throw new SQLException("targetDS doesn't implement XADataSource interface.");
	}

	public XAConnection getXAConnection(String user, String password)
			throws SQLException {
		if(targetDS instanceof XADataSource)
			return XAConnectionLoggingProxy.wrap(((XADataSource)targetDS).getXAConnection(user, password));
		else
			throw new SQLException("targetDS doesn't implement XADataSource interface.");
	}

	public PooledConnection getPooledConnection() throws SQLException {
		if(targetDS instanceof ConnectionPoolDataSource)
			return PooledConnectionLoggingProxy.wrap(((ConnectionPoolDataSource)targetDS).getPooledConnection());
		else
			throw new SQLException("targetDS doesn't implement ConnectionPoolDataSource interface.");
	}

	public PooledConnection getPooledConnection(String user, String password)
			throws SQLException {
		if(targetDS instanceof ConnectionPoolDataSource)
			return PooledConnectionLoggingProxy.wrap(((ConnectionPoolDataSource)targetDS).getPooledConnection(user, password));
		else
			throw new SQLException("targetDS doesn't implement ConnectionPoolDataSource interface.");
	}
	
	void invokeTargetSetMethod(String m, Object p) {
		String methodName = "invokeTargetSetMethod() ";
		if(targetDS == null) {
			props.put(m, p);
			return;
		}
		logger.debug(m + "(" + p.toString() + ")");
		try {
			Method me = targetDS.getClass().getMethod(m,
					new Class[] { String.class });
			if (me != null)
				me.invoke(targetDS, new Object[] { p });
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	public void setURL(String url) throws JDBCDSLogException {
			url = initTargetDS(url);
			invokeTargetSetMethod("setURL", url);
	}

	private String initTargetDS(String url) throws JDBCDSLogException {
		String methodName = "initTargedDS() ";
		logger.debug(methodName + "url = " + url + " targedDS = " + targetDS);
		try {
			if(url == null || targetDS != null)
				return url;
			logger.debug("Parse url.");
			StringTokenizer ts = new StringTokenizer(url, ":/;=&?", false);
			String targetDSName = null;
			while (ts.hasMoreTokens()) {
				String s = ts.nextToken();
				logger.debug("s = " + s);
				if (targetDSParameter.equals(s) && ts.hasMoreTokens()) {
					targetDSName = ts.nextToken();
					break;
				}
			}
			if (targetDSName == null)
				return url;
			url = url.substring(0, url.length() - targetDSName.length() - targetDSParameter.length() - 2);
			Class cl = Class.forName(targetDSName);
			if (cl == null)
				throw new JDBCDSLogException("Can't load class of targetDS.");
			Object targetObj = cl.newInstance();
			targetDS = targetObj;
			logger.debug(methodName + "targetDS initialized.");
			setPropertiesForTargetDS();
			return url;
		} catch (Throwable t) {
			logger.error(t.getMessage(), t);
			throw new JDBCDSLogException(t);
		}
	}
	
	private void setPropertiesForTargetDS() {
		for(Iterator i = props.keySet().iterator(); i.hasNext(); ) {
			String m = (String)i.next();
			invokeTargetSetMethod(m, props.get(m));
		}
	}

	public void setDatabaseName(String p) {
		invokeTargetSetMethod("setDatabaseName", p);
	}
	
	public void setDescription(String p) {
		invokeTargetSetMethod("setDescription", p);
	}
	
	public void setDataSourceName(String p) {
		invokeTargetSetMethod("setDataSourceName", p);
	}
	
	public void setDriverType(String p) {
		invokeTargetSetMethod("setDriverType", p);
	}
	
	public void setNetworkProtocol(String p) {
		invokeTargetSetMethod("setNetworkProtocol", p);
	}
	
	public void setPassword(String p) {
		invokeTargetSetMethod("setPassword", p);
	}
	
	public void setPortNumber(int p) {
		invokeTargetSetMethod("setPortNumber", new Integer(p));
	}
	
	public void setServerName(String p) {
		invokeTargetSetMethod("setServerName", p);
	}
	
	public void setServiceName(String p) {
		invokeTargetSetMethod("setServiceName", p);
	}
	
	public void setTNSEntryName(String p) {
		invokeTargetSetMethod("setTNSEntryName", p);
	}
	
	public void setUser(String p) {
		invokeTargetSetMethod("setUser", p);
	}
	
	public void setDatabase(String p) throws JDBCDSLogException {
		p = initTargetDS(p);
		invokeTargetSetMethod("setDatabase", p);
	}

	public boolean isWrapperFor(Class iface) throws SQLException {
		return false;
	}

	public Object unwrap(Class iface) throws SQLException {
		return null;
	}

}