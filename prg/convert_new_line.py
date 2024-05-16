import os
import sys

def convert_newlines(file_path):
    with open(file_path, 'rb') as file:
        binary_content = file.read()
    
    modified_content = binary_content.replace(b'\r\n', b'\n').decode('utf-8')
    
    with open(file_path, 'w', encoding='utf-8', newline='\n') as file:
        file.write(modified_content)

def convert_directory_newlines(directory_path):
    for filename in os.listdir(directory_path):
        file_path = os.path.join(directory_path, filename)
        if os.path.isfile(file_path):
            print("Converting:", file_path)
            convert_newlines(file_path)

def main():
    # require a folder name as a command line argument
    if len(sys.argv) != 2:
        print("Usage: python convert_new_line.py <folder_name>")
        sys.exit(1)
    
    folder_name = sys.argv[1]
    current_directory = os.getcwd()
    full_directory_path = os.path.join(current_directory, folder_name)
    
    convert_directory_newlines(full_directory_path)

if __name__ == "__main__":
    main()
        