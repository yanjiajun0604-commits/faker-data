package faker.data.extension;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 运行用户明确安装的可信本地代码；独立进程不等于操作系统安全沙箱。 */
final class ExtensionRunner {
    static Path run(ObjectMapper mapper,JsonNode spec,Path extension,Path job,JsonNode input) throws IOException {
        Path inputFile=job.resolve("input.json"),output=job.resolve("output.json"),log=job.resolve("extension.log");
        mapper.writeValue(inputFile.toFile(),input);
        JsonNode command=spec.path("command");
        if(!command.isArray() || command.size()==0 || command.size()>100)throw new IllegalArgumentException("INVALID_EXTENSION_COMMAND");
        List<String> args=new ArrayList<>();
        for(JsonNode item:command) {
            if(!item.isTextual())throw new IllegalArgumentException("INVALID_EXTENSION_COMMAND");
            String value=item.asText().replace("${input}",inputFile.toString()).replace("${output}",output.toString()).replace("${extension}",extension.toString());
            if(value.contains("${"))throw new IllegalArgumentException("UNKNOWN_EXTENSION_PLACEHOLDER");
            args.add(value);
        }
        if(!Paths.get(args.get(0)).isAbsolute())throw new IllegalArgumentException("EXTENSION_EXECUTABLE_MUST_BE_ABSOLUTE");
        int timeout=spec.path("timeoutSeconds").asInt(60);
        if(timeout<1 || timeout>600)throw new IllegalArgumentException("INVALID_EXTENSION_TIMEOUT");
        ProcessBuilder builder=new ProcessBuilder(args).directory(job.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        Map<String,String> inherited=System.getenv();builder.environment().clear();
        for(String name:Arrays.asList("SystemRoot","WINDIR","PATH","TEMP","TMP","JAVA_HOME","LANG"))
            if(inherited.containsKey(name))builder.environment().put(name,inherited.get(name));
        builder.environment().put("PYTHONIOENCODING","utf-8");
        Process process=builder.start();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(timeout);
        try {
            while(!process.waitFor(100,TimeUnit.MILLISECONDS)) {
                if(System.nanoTime()>deadline)throw new IllegalStateException("EXTENSION_TIMEOUT");
                if(Files.exists(log) && Files.size(log)>1048576)throw new IllegalStateException("EXTENSION_LOG_LIMIT");
            }
            if(process.exitValue()!=0)throw new IllegalStateException("EXTENSION_PROCESS_FAILED");
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();throw new IllegalStateException("EXTENSION_INTERRUPTED");
        } finally {if(process.isAlive())process.destroyForcibly();}
        if(!Files.isRegularFile(output) || !output.toRealPath().startsWith(job.toRealPath()) || Files.size(output)>16777216)
            throw new IllegalArgumentException("INVALID_EXTENSION_OUTPUT");
        return output;
    }
}
