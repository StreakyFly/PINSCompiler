#!/bin/bash

# Java version: 21

# This script runs the tests for the PINS24 project.
# The script takes a path to a test file or directory as an argument.
# If the path is a directory, the script runs all the test files in the directory.

# The script assumes that the test files are in the following format:
# - The test file has the extension .pins24
# - The output file has the same name as the test file with the extension _out.pins24

# Example of usage:
# ./test_runner.sh LexAn successful.pins24
# ./test_runner.sh LexAn LexAn
# Warning: you have to run the script from the prg/ directory, which should be in the root of the project
# Warning 2: in output files, make sure to add a newline at the end of the file

echo_color() {
    local color=$1
    local text=$2
    case $color in
        "red")
            echo -e "\033[1;31m$text\033[0m"
            ;;
        "green")
            echo -e "\033[1;32m$text\033[0m"
            ;;
        "yellow")
            echo -e "\033[0;33m$text\033[0m"
            ;;
            
        "blue")
            echo -e "\033[1;34m$text\033[0m"
            ;;
        *)
            echo "Unsupported color. Please choose from: green, red, blue, yellow."
            ;;
    esac
}

function run_test {
    echo_color "blue" "---> Running test: $2"

    # run java project in ../out catch error too
    java -p ../out/production/pins24 -m pins24/pins24.phase.$1 $2 > /tmp/test_output.pins24 2>&1

    # Strip ANSI codes from the test output - WARNING, this could cause incorrect TEST FAILED messages if ANSI codes are included in test.pins24 strings
    # sed 's/\x1b\[1m//g; s/\x1b\[0m//g' /tmp/test_output.pins24 > /tmp/stripped_test_output.pins24
    # sed 's/\x1b\[1m//g; s/\x1b\[0m//g; s/\x1b\[31m//g; s/\x1b\[30m//g' /tmp/test_output.pins24 > /tmp/stripped_test_output.pins24   
    sed 's/\x1b\[[0-9;]*m//g' /tmp/test_output.pins24 > /tmp/stripped_test_output.pins24

    diff -q /tmp/stripped_test_output.pins24 $3 > /dev/null

    # check if the test passed
    if [ $? -eq 0 ]; then
        echo_color "green" "   √ Test passed"
    else
        echo_color "red" "   X Test failed"

        echo_color "yellow" "   └ Expected output (left) and actual output (right):"
        diff -y $3 /tmp/stripped_test_output.pins24
        echo ""
    fi

    # remove the output files
    rm /tmp/test_output.pins24 /tmp/stripped_test_output.pins24
}

function is_output_file {
    # check if file has _out.pins24 extension
    if [[ $1 == *_out.pins24 || $1 == *.out ]]; then
        return 0
    else
        return 1
    fi
}

function has_extension {
    # check if file has .pins24 extension
    if [[ $1 == *.pins24 || $1 == *.out ]]; then
        return 0
    else
        return 1
    fi
}

function check_build {
    # check if the project has been built
    if [ ! -d ../out ]; then
        echo_color "red" "Error: Please build project with IntelliJ before running tests (make sure that out/ directory exists in project root)."
        exit 1
    fi

    # check if phase class is in ../out/production/pins24/pins24/phase
    if [ ! -f ../out/production/pins24/pins24/phase/$1.class ]; then
        echo_color "red" "Error: $1.class not found in ../out/production/pins24/pins24/phase"
        exit 1
    fi
}

# check if the user has provided a path to the test file or directory and name of the phase to run
if [ "$#" -ne 2 ]; then
    echo_color "yellow" "Usage: ./test_runner.sh <phase> <file or directory>"
    echo_color "yellow" "Please provide a path to the test file or directory and name of the phase to run."
    exit 1
fi

# check build before running the tests
check_build "$1"

# check if the path is a directory
if [ -d "$2" ]; then
    echo_color "yellow" "Running tests for $2..."
    for file in "$2"/*; do
        if [ -f "$file" ]; then
            # check if the file has a .pins24 extension
            if ! has_extension "$file"; then
                continue
            fi

            # check if the file is an output file
            if is_output_file "$file"; then
                continue
            fi

            # check if the output file exists
            output_file="${file%.*}_out.pins24"
            output_file2="${file%.*}.out"
            if [[ -f "$output_file" || -f "$output_file2" ]]; then
                if [ -f "$output_file" ]; then
                    echo "Output file found: $output_file"
                    # run the test
                    run_test "$1" "$file" "$output_file"
                elif [ -f "$output_file2" ]; then
                    echo "Output file found: $output_file2"
                    # run the test
                    run_test "$1" "$file" "$output_file2"
                else
                    echo "Error: Neither $output_file nor $output_file2 found."
                    exit 1
                fi
            else
                echo "The file '$file' does not match the criteria."
            fi
        fi
    done
elif [ -f "$2" ]; then # check if the path is a file
    # check if the file is an output file
    if is_output_file "$2"; then
        echo_color "red" "Error: $2 is an output file. Please provide a test file."
        exit 1
    fi

    # check if the file has a .pins24 extension
    if ! has_extension "$2"; then
        echo_color "red" "Error: $2 does not have a .pins24 extension."
        exit 1
    fi

    # check if the output file exists
    output_file="${2%.*}_out.pins24"
    if [ ! -f "$output_file" ]; then
        echo_color "red" "Error: $output_file not found."
        exit 1
    fi


    # run the test
    run_test "$1" "$2" "$output_file"
else
    echo_color "red" "Error: $2 is not a valid file or directory."
    exit 1
fi

