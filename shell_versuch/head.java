import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import static cTools.KernelWrapper.*;

public class head {
    public static void main(String[] args) {

        Writer stdout = new BufferedWriter(new OutputStreamWriter(System.out));

        int n = 10;
        int c = -1;

        List<String> argList = new LinkedList<>(Arrays.asList(args));

        ListIterator<String> it = argList.listIterator();
        try {
            while (it.hasNext()) {
                // TODO: ignore invalid flags?
                switch (it.next()) {
                    case "-c":
                        it.remove();
                        c = Integer.parseInt(it.next());
                        it.remove();
                        break;
                    case "-n":
                        it.remove();
                        n = Integer.parseInt(it.next());
                        it.remove();
                        break;
                    case "--help":
                        System.out.println("// TODO: Help here.");
                        exit(0);
                        break;
                }
            }
        } catch (NumberFormatException e) {
            System.out.println("Not a valid integer after -c or -n");
            exit(1);
        }

        if (argList.size() == 0) {
            argList.add("-");
        }
        try {
            for (String filename : argList) {
                int fd;

                if (filename.equals("-")) {
                    // Use stdin
                    fd = STDIN_FILENO;
                } else {
                    // Open file
                    if ((fd = open(filename, O_RDONLY)) == -1) {
                        System.out.println("Error opening " + filename);
                        exit(1);
                    }
                }

                final int bufferSize = c > 0 ? c : 256;
                byte[] buffer = new byte[bufferSize];
                int lastReadCount = 0;
                int lineCounter = 0;
                int byteCounter = 0;

                do {
                    lastReadCount = read(fd, buffer, bufferSize);

                    // Condition: Stay in buffer, stay in line limit or byte limit (use c to decide whitch)
                    for (int i = 0; i < lastReadCount && (c > 0 ? byteCounter < c : lineCounter < n); i++) {
                        if (buffer[i] == '\n') {
                            lineCounter++;
                            stdout.flush();
                        }

                        stdout.write(buffer[i]);
                        byteCounter++;
                    }


                    // Stop condition: all lines printed or EOF encountered
                } while (lineCounter < n && lastReadCount != 0);

                if (c > 0) {
                    // TODO decide if we should do this
                    // If writing by byte count, add newline before printing next file
                    stdout.write('\n');
                }
                stdout.flush();
            }
        } catch (IOException e) {
            System.out.print("Error writing to stdout via BufferedWriter:");
            e.printStackTrace();
            exit(1);
        }

        exit(0);
    }
}
