package faker.data.extension;
import java.security.MessageDigest;
import java.util.Locale;

/** 本地字段扩展共享的标识符和文件摘要校验。 */
final class ExtensionSupport {
    static void identifier(String name,boolean qualified) {
        String part="[\\p{L}_][\\p{L}\\p{N}_]*";
        if(name==null || !name.matches(qualified?part+"(?:\\."+part+")?":part))throw new IllegalArgumentException("INVALID_EXTENSION_IDENTIFIER");
    }
    static String sha256(byte[] bytes)throws Exception {
        StringBuilder hash=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(Locale.ROOT,"%02x",b&255));
        return hash.toString();
    }
}
