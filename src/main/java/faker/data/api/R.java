package faker.data.api;

import java.util.HashMap;
import java.util.Map;

public class R extends HashMap<String, Object> {
	private static final long serialVersionUID = 1L;

	public R() {
		put("code", 0);
		put("msg", "操作成功");
	}

	public static R error() {
		return error(1, "操作失败");
	}

	public static R error(String msg) {
		return error(1, msg);
	}

	public static R error(int code, String msg) {
		R r = new R();
		r.put("code", code);
		r.put("msg", msg);
		return r;
	}

	public static R ok(String msg) {
		R r = new R();
		r.put("msg", msg);
		return r;
	}

	public static R ok(Map<String, Object> map) {
		if (map.containsKey("code")) {
			try {
				Integer.parseInt("" + map.get("code"));
			} catch (Exception e) {
				throw new RuntimeException("Then 'code' key must be integer");
			}
		}

		R r = new R();
		r.putAll(map);
		return r;
	}

	public static R ok() {
		return new R();
	}

	@Override
	public R put(String key, Object value) {
		if (key.equals("code")) {
			try {
				Integer.parseInt("" + value);
			} catch (Exception e) {
				throw new RuntimeException("Then 'code' key must be integer");
			}
		}

		super.put(key, value);
		serverError();

		return this;
	}

	private void serverError() {
		if (containsKey("error") && containsKey("status") && "Internal Server Error".equals((String) get("error"))) {
			super.put("code", get("status"));
		}
	}

	public int getCode() {
		Object code = get("code");
		if (code == null) {
			return 0;
		} else {
			return (Integer) code;
		}
	}

	public String getMsg() {
		return (String) get("msg");
	}

}
