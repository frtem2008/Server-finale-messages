package IO.Files;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;


record LogFile(String name, File file, LogFileType type) {
    
    LogFile {
        try {
            if (file != null && !file.exists())
                if (!file.createNewFile())
                    throw new RuntimeException("Failed to create log file in: " + file.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create log file in: " + file.getAbsolutePath() + "\n" + e.getLocalizedMessage());
        }
    }

    
    public void clear() {
        checkAccess();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            writer.write("");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    
    public String getName() {
        return name;
    }

    
    public LogFileType getType() {
        return type;
    }

    
    public File getFile() {
        return file;
    }

    
    public boolean hasName(String str) {
        return name.equals(str);
    }

    
    public boolean hasType(LogFileType tp) {
        return type.equals(tp);
    }

    
    public boolean isValid() {
        return file != null && file.exists() && file.isFile();
    }

    
    private void checkAccess() {
        if (!isValid()) {
            if (file == null)
                throw new IllegalStateException("Invalid log file: file is null");
            if (!file.exists())
                throw new IllegalStateException("Invalid log file: " + file.getAbsolutePath() + " does not exist");
            if (!file.isFile())
                throw new IllegalStateException("Invalid log file: " + file.getAbsolutePath() + " is not a file");
        }
    }

    
    public boolean doIf(Predicate<LogFile> predicate, Consumer<LogFile> action) {
        if (predicate.test(this))
            action.accept(this);
        return true;
    }

    
    public boolean doIf(Predicate<LogFile> predicate, Function<LogFile, Boolean> action) {
        if (predicate.test(this))
            return action.apply(this);
        return false;
    }

    
    public void log(String str) {
        checkAccess();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write(str + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


public class FileLogger {
    
    private final Set<LogFile> files;

    
    private final String logDirAbsPath;

    
    public FileLogger(String logDirPath) {
        File dir = new File(logDirPath);
        logDirAbsPath = dir.getAbsolutePath() + "\\";
        if (!dir.exists() && !dir.mkdirs())
            throw new RuntimeException("Failed to create logging directory in: " + logDirAbsPath);
        files = new HashSet<>();
    }

    
    public String getLogDirPath() {
        return logDirAbsPath;
    }

    
    public void addLogFile(String name, String fileName, LogFileType type) {
        files.add(new LogFile(name, new File(logDirAbsPath + fileName), type));
    }

    
    private LogFile getLogFileFile(String name) {
        for (LogFile file : files)
            if (file.hasName(name))
                return file;
        throw new RuntimeException("Log file with name: " + name + " not found in file list");
    }

    
    public File getLogFile(String name) {
        return getLogFileFile(name).getFile();
    }

    
    public void printFileInfo(Consumer<String> printer, String name) {
        LogFile f = getLogFileFile(name);
        printer.accept(f.getName() + ": " + f.getFile().getAbsolutePath());
    }

    
    public String getLogFilePath(String name) {
        return getLogFileFile(name).getFile().getAbsolutePath();
    }

    
    private boolean logIf(LogFile file, Predicate<LogFile> predicate, String str) {
        return file.doIf(predicate, (Consumer<LogFile>) logFile -> log(logFile, str));
    }

    
    private void forAllFiles(Consumer<LogFile> action) {
        files.forEach(action);
    }

    
    private void forOneFile(Function<LogFile, Boolean> action) {
        for (LogFile file : files)
            if (action.apply(file))
                return;
    }

    
    public void clearAll(LogFileType type) {
        forAllFiles(file -> file.doIf(logFile -> logFile.hasType(type), LogFile::clear));
    }

    
    public void clearAll(String name) {
        forAllFiles(file -> file.doIf(logFile -> logFile.hasName(name), LogFile::clear));
    }

    
    public void clearOne(LogFileType type) {
        forOneFile(file -> file.doIf(logFile -> logFile.hasType(type), LogFile::clear));
    }

    
    public void clearOne(String name) {
        forOneFile(file -> file.doIf(logFile -> logFile.hasName(name), LogFile::clear));
    }

    
    public void logToAll(LogFileType type, String str) {
        forAllFiles(file -> logIf(file, logFile -> logFile.hasType(type), str));
    }

    
    public void logToAll(String name, String str) {
        forAllFiles(file -> logIf(file, logFile -> logFile.hasName(name), str));
    }


    
    public void logToOne(LogFileType type, String str) {
        forOneFile(file -> logIf(file, logFile -> logFile.hasType(type), str));
    }

    
    public void logToOne(String name, String str) {
        forOneFile(file -> logIf(file, logFile -> logFile.hasName(name), str));
    }

    
    public void log(LogFile logFile, String str) {
        logFile.log(str);
    }
}
