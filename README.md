# Table of Contents

1. [About](README.md#About)
2. [Requirements](README.md#Requirements)
3. [Building](README.md#Building)
4. [Running](README.md#Running)



# About

This is a submission to the Insight Data Engineering Fellowship Program's Coding Challenge.

The program traverses a log file of user access statistics for the
US Securities and Exchange Commission (SEC) website,
to detect user sessions based on the user's IP address and the session timeout
specified in another input file.

The results are output in the form of a text file, with each line containing
information about a single access session with the following comma-separated fields:

* User's IP as provided by the log
* The session's starting time
* The session's ending time
* The session's duration, in seconds, inclusive of both the starting and ending times
* The number of documents requested

The program is written in Scala using only the Scala standard library.



# Requirements

The following are requred with their respective dependencies:

* Bash shell
* Scala compiler and interpreter with the binary version >= 2.10
* jar executable



# Building

To build the sources and package the **.jar** file,
execute
```
./build.sh
```



# Running

To run the program, execute
```
./run.sh
```
If the **.jar** file isn't found, this will run the build first.
You can pass the program arguments directly to this script.
The program expects 3 arguments, each a file's pathname, in the following order:

1. The input log. The log should be in the format
   [provided](https://www.sec.gov/dera/data/edgar-log-file-data-set.html)
    by the Department of Economic and Risk Analysis (DERA) of SEC.
2. A text file containing a single integer on the first line specifying
   the session timeout in seconds.
3. An output file

If fewer than 3 arguments are passed to the script, it will default to
 `./input/log.csv` for the first argument,
 `./input/inactivity_period.txt` for the second, and
 `./output/sessionization.txt` for the third.
