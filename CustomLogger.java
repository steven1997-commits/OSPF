import java.io.FileWriter;
import java.io.File;
import java.io.IOException;
import java.io.*;

public class CustomLogger {

    private File logFile;
    private FileWriter logFileWriter;

    public CustomLogger(String name) {
        try {
            this.logFile = new File(name + ".log");
            if (this.logFile.exists()) {
                this.logFile.delete();
            }
            this.logFile.createNewFile();
            this.logFileWriter = new FileWriter(this.logFile, true);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void log(String message) {
        try {
            this.logFileWriter.write(message + '\n');
            this.logFileWriter.flush();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}