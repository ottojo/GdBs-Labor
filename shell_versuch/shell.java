import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

import static cTools.KernelWrapper.*;

class shell {


    public static void main(String[] args) throws IOException {
        BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
        String prompt = ">>>";

        while (true) {
            System.out.print(prompt);

            String rawIn = inReader.readLine();

            List<String> input = new LinkedList<>(Arrays.asList(rawIn.split("\\s+")));

            List<Integer> pidsForCommand = executeAndGetPIDs(input, STDIN_FILENO);

            // Wait for all child processes
            for (int pid : pidsForCommand) {
                waitpid(-1, new int[2], 0);
            }
        }
    }


    private static List<Integer> executeAndGetPIDs(List<String> cmdLine, int stdin) {

        List<Integer> pids = new LinkedList<>();

        if ("exit".equals(cmdLine.get(0))) {
            exit(0);
        }

        File executable = new File(cmdLine.get(0));

        if (!executable.exists()) {
            System.err.printf("File %s does not exist.\n", executable.getAbsolutePath());
            System.out.println();
            return pids;
        }

        if (!executable.canExecute()) {
            System.err.println("File is not executable.");
            System.out.println();
            return pids;
        }

        List<String> remainingAfterPipe = new LinkedList<>();
        Iterator<String> pipeIterator = cmdLine.iterator();
        while (pipeIterator.hasNext()) {
            if (pipeIterator.next().charAt(0) == '|') {
                pipeIterator.remove();
                while (pipeIterator.hasNext()) {
                    remainingAfterPipe.add(pipeIterator.next());
                    pipeIterator.remove();
                }

            }
        }

        int[] p = new int[2];
        boolean redirectToPipe = false;
        if (remainingAfterPipe.size() != 0) {

            // Our command contains a pipe and something after it.
            // Create a pipe.

            if (pipe(p) == -1) {
                System.err.println("Error creating pipe after " + executable.getAbsolutePath());
                System.exit(1);
            }

            // Remember to set stdout in the fork to the pipe
            redirectToPipe = true;
        }


        int forkPid = fork();
        if (forkPid == 0) {
            // Is child

            // Use specified stdin
            dup2(stdin, STDIN_FILENO);

            setFileIO(cmdLine);

            if (redirectToPipe) {
                // We only want to write to the pipe, close the reading end.
                close(p[0]);

                // Replace stdout with write end of pipe
                dup2(p[1], STDOUT_FILENO);
            }

            // Replace this process
            eexec(cmdLine);

        } else if (forkPid == -1) {
            System.err.println("Error forking.");
            System.out.println();
        } else {
            // Is parent process

            pids.add(forkPid);

            // If we had a pipe following this command, execute the rest.
            if (redirectToPipe) {
                // We dont need the write end of the pipe.
                close(p[1]);
                pids.addAll(executeAndGetPIDs(remainingAfterPipe, p[0]));
            }
        }

        return pids;
    }

    /**
     * Exec with options, supports expansion
     *
     * @param cmdLine command with options
     */
    private static void eexec(List<String> cmdLine) {
        expandAll(cmdLine);

        if (execv(cmdLine.get(0), cmdLine.toArray(String[]::new)) == -1) {
            System.err.println("Error executing.");
            exit(1);
        }
    }

    private static void expandAll(List<String> cmdLine) {
        ListIterator<String> itr = cmdLine.listIterator();
        while (itr.hasNext()) {
            String element = itr.next();
            List<String> exp = expand(element);
            if (exp.size() > 0) {
                itr.remove();
                exp.forEach(itr::add);
            }
        }
    }

    /**
     * Replaces stdin and stdout with files specified by "<" and ">".
     * Removes those options from the cmdLine
     *
     * @param cmdLine command with options and optional > <
     */
    private static void setFileIO(List<String> cmdLine) {
        Iterator<String> itr = cmdLine.iterator();
        while (itr.hasNext()) {
            switch (itr.next().charAt(0)) {
                case '<':
                    itr.remove();
                    close(STDIN_FILENO);
                    open(itr.next(), O_RDONLY);
                    itr.remove();
                    break;
                case '>':
                    itr.remove();
                    close(STDOUT_FILENO);
                    open(itr.next(), O_WRONLY | O_CREAT);
                    itr.remove();
                    break;
            }
        }
    }

    private static List<String> expand(String input) {
        String globAsRegex = input.replace("*", ".*").replace("?", ".");
        File[] fileList = new File(".").listFiles(pathname -> pathname.getName().matches(globAsRegex));
        return Arrays.stream(Objects.requireNonNull(fileList)).map(File::getName).collect(Collectors.toList());
    }
}
