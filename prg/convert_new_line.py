import os

def convert_newlines(file_path):
    # Read the original content of the file in binary mode to catch all \r\n instances
    with open(file_path, 'rb') as file:
        binary_content = file.read()
    
    # Convert binary content to text, replacing \r\n with \n
    modified_content = binary_content.replace(b'\r\n', b'\n').decode('utf-8')
    
    # Write the modified content back to the file in text mode
    with open(file_path, 'w', encoding='utf-8', newline='\n') as file:
        file.write(modified_content)

def convert_directory_newlines(directory_path):
    # List all files in the given directory
    for filename in os.listdir(directory_path):
        file_path = os.path.join(directory_path, filename)
        
        # Check if it is a file, not a directory or a link
        if os.path.isfile(file_path):
            print("Converting:", file_path)
            convert_newlines(file_path)

DIR = 'C:/Users/mihac/Desktop/PNS - prevajalniki in navidezni stroji/pins24/prg/Abstr/'

convert_directory_newlines(DIR)
