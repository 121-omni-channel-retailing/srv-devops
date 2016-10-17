package otocloud.servermanager.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SQLUtils {
	public static String getQuerySql(String tableName, String[] queryFields, JsonObject parameters,
			JsonArray paramValues) {
		StringBuilder sql = new StringBuilder();
		sql.append("select ");
		if (queryFields == null || queryFields.length == 0) {
			sql.append("* ");
		} else {
			for (String queryField : queryFields) {
				sql.append(queryField).append(",");
			}
			sql.setLength(sql.length() - 1);
		}
		sql.append(" from ").append(tableName);
		if (parameters != null && parameters.size() > 0) {
			sql.append(" where ");
			parameters.forEach(entry -> {
				sql.append(entry.getKey()).append(" = ? ").append("and ");
				Object paramValue = entry.getValue();
				if (paramValue instanceof JsonObject || paramValue instanceof JsonArray) {
					paramValue = paramValue.toString();
				}
				paramValues.add(paramValue);
			});
			sql.setLength(sql.length() - 4);
		}
		return sql.toString();
	}
}
