# Table of Contents

1. [About](README.md#About)
2. [Requirements](README.md#Requirements)
3. [Running and Running](README.md#Building-and-Running)



# About

This is a submission to the Insight Data Engineering Fellowship Program's Coding Challenge.

The program traverses a log file of user access statistics for the US Securities and Exchange Commission (SEC) website, to detect user sessions based on the user IP addresses and the specified session timeout.

The results are written to a text file with each line containing the information about a single detected session with the following comma-separated fields:

* User IP as provided by the log
* Session starting time
* Session ending time
* Session duration, in seconds inclusive of the starting and ending times
* Number of documents requested

The program is written in Scala with just the standard library.

**Author**: Arseny A. Egorov, <arsegorov@gmail.com>



# Requirements

The following tools are required:

* Bash shell
* Scala compiler and interpreter with the binary version >= 2.10
* `jar` executable



# Building and Running

To build, and then run the program, execute:

    ./run.sh

If `edgar.jar` file isn't found, the script will build the **.jar** first, and then will run it. If the **.jar** is present, the script will skip the build part.

You can pass the program arguments directly to the script. The program expects 3 arguments, each a file pathname, in the following order:

1. A user activity log&mdash;the log should be in the format
   [provided](https://www.sec.gov/dera/data/edgar-log-file-data-set.html)
    by SEC's Department of Economic and Risk Analysis (DERA)
2. A text file containing a single integer on the first line specifying
   the session timeout in seconds
3. An output file

If fewer than 3 arguments are passed to `run.sh`, it will default to `./input/log.csv` for the first argument, `./input/inactivity_period.txt` for the second, and `./output/sessionization.txt` for the third.
