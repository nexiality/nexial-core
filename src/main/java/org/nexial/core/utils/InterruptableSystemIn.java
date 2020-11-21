package org.nexial.core.utils;

import java.util.Scanner;
import java.util.concurrent.Semaphore;

/*
 * Required for issue https://keanlight.atlassian.net/browse/NEX-96
 */
public class InterruptableSystemIn {
    protected static Scanner input = new Scanner(System.in);
    protected static final Semaphore waitingForInput = new Semaphore(0, true);
        //If InterruptableSysIn is waiting on input.nextLine(); Can also be cleared by cancel();
    protected static String currentLine = null; //What the last scanned-in line is
    private static final Semaphore waitingOnOutput = new Semaphore(1);
        // If there's someone waiting for output. Used for thread safety
    private static boolean canceled = false; //If the last input request was cancelled.
    private static boolean ignoreNextLine = false;
        //If the last cancel() call indicated input should skip the next line.
    private static final String INTERRUPTED_ERROR = "\nInterrupted"; // null
    private static final String INUSE_ERROR = null;
    private static boolean lastLineInterrupted = false;

    /**
     * This method will block if someone else is already waiting on a next line.
     * Guarantees on fifo order - threads are paused, and enter a queue if the
     * input is in use at the time of request, and will return in the order the
     * requests were made
     *
     * @return The next line from System.in, or "\nInterrupted" if it's interrupted for any reason
     */
    public static String nextLineBlocking() {
        //Blocking portion
        try {
            waitingOnOutput.acquire(1);
        } catch (InterruptedException iE) {
            return INTERRUPTED_ERROR;
        }
        String toReturn = getNextLine();
        waitingOnOutput.release(1);
        return toReturn;
    }

    /**
     * This method will immediately return if someone else is already waiting on a next line.
     *
     * @return The next line from System.in, or
     * "\nInterrupted" if it's interrupted for any reason or
     * "\nInUse" if the scanner is already in use
     */
    public static String nextLineNonBlocking() {
        //Failing-out portion
        if (!waitingOnOutput.tryAcquire(1)) {
            return INUSE_ERROR;
        }
        String toReturn = getNextLine();
        waitingOnOutput.release(1);
        return toReturn;
    }

    /**
     * This method will block if someone else is already waiting on a next line.
     * Guarantees on fifo order - threads are paused, and enter a queue if the
     * input is in use at the time of request, and will return in the order the
     * requests were made
     *
     * @param ignoreLastLineIfUnused If the last line was canceled or Interrupted, throw out that line, and wait for a new one.
     * @return The next line from System.in, or "\nInterrupted" if it's interrupted for any reason
     */
    public static String nextLineBlocking(boolean ignoreLastLineIfUnused) {
        ignoreNextLine = ignoreLastLineIfUnused;
        return nextLineBlocking();
    }

    /**
     * This method will fail if someone else is already waiting on a next line.
     *
     * @param ignoreLastLineIfUnused If the last line was canceled or Interrupted, throw out that line, and wait for a new one.
     * @return The next line from System.in, or
     * "\nInterrupted" if it's interrupted for any reason
     * "\nInUse" if the scanner is already in use
     */
    public static String nextLineNonBlocking(boolean ignoreLastLineIfUnused) {
        ignoreNextLine = ignoreLastLineIfUnused;
        return nextLineNonBlocking();
    }

    public static String getNextLine() {
        //Cache the current line on the very off chance that some other code will run between the next few lines
        String toReturn = currentLine;

        if (canceled) {//If the last one was cancelled
            canceled = false;

            //If there has not been a new line since the cancellation
            if (toReturn.equalsIgnoreCase(INTERRUPTED_ERROR)) {
                //If the last request was cancelled, and has not yet received an input

                //wait for that input to finish
                toReturn = waitForLineToFinish();
                //If the request to finish the last line was interrupted
                if (toReturn.equalsIgnoreCase(INTERRUPTED_ERROR)) {
                    return null;
                }

                if (ignoreNextLine) {
                    //If the last line is supposed to be thrown out, get a new one
                    ignoreNextLine = false;
                    //Request an input
                    toReturn = getLine();
                } else {
                    return toReturn;
                }

                //If there has been a new line since cancellation
            } else {
                //If the last request was cancelled, and has since received an input
                try {
                    waitingForInput
                        .acquire(1); //Remove the spare semaphore generated by having both cancel() and having input
                } catch (InterruptedException ignore) {
                    return INTERRUPTED_ERROR;
                }

                if (ignoreNextLine) {
                    ignoreNextLine = false;
                    //Request an input
                    toReturn = getLine();
                }
                //return the last input
                return toReturn;
            }
        } else {
            if (lastLineInterrupted) {

                //wait for that input to finish
                toReturn = waitForLineToFinish();
                //If the request to finish the last line was interrupted
                if (toReturn.equalsIgnoreCase(INTERRUPTED_ERROR)) {
                    return null;
                }

                //Should the read be thrown out?
                if (ignoreNextLine) {
                    //Request an input
                    toReturn = getLine();
                }

            } else {
                ignoreNextLine = false; //If it's been set to true, but there's been no cancellation, reset it.

                //If the last request was not cancelled, and has not yet received an input
                //Request an input
                toReturn = getLine();
            }
        }
        return toReturn;
    }

    public static String waitForLineToFinish() {
        //If the last request was interrupted
        //wait for the input to finish
        try {
            waitingForInput.acquire(1);
            lastLineInterrupted = false;
            canceled = false;
            return currentLine;
        } catch (InterruptedException iE) {
            lastLineInterrupted = true;
            return INTERRUPTED_ERROR;
        }
    }

    /**
     * Cancels the currently waiting input request
     */
    public static void cancel() {
        if (!waitingOnOutput.tryAcquire(1)) { //If there is someone waiting on user input
            canceled = true;
            currentLine = INTERRUPTED_ERROR;
            waitingForInput
                .release(1); //Let the blocked scanning threads continue, or restore the lock from tryAcquire()
        } else {
            waitingOnOutput.release(1); //release the lock from tryAcquire()
        }
    }

    public static void cancel(boolean throwOutNextLine) {
        if (!waitingOnOutput.tryAcquire(1)) { //If there is someone waiting on user input
            canceled = true;
            currentLine = INTERRUPTED_ERROR;
            ignoreNextLine = throwOutNextLine;
            waitingForInput.release(1); //Let the blocked scanning threads continue
        } else {
            waitingOnOutput.release(1); //release the lock from tryAcquire()
        }
    }

    private static String getLine() {
        Thread ct = new Thread(() -> {
            try {
                currentLine = input.nextLine();
            } catch (IndexOutOfBoundsException ie) {
                currentLine = null;
            }
            waitingForInput.release(1);
        });
        ct.start();
        //Makes this cancelable
        try {
            waitingForInput.acquire(1); //Wait for the input
        } catch (InterruptedException iE) {
            cancel();
            lastLineInterrupted = true;
            return INTERRUPTED_ERROR;
        }
        if (canceled) {
            return INTERRUPTED_ERROR;
        }
        return currentLine;
    }

}