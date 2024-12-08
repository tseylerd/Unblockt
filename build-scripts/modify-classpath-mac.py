import sys


def reorder_lines(file_path):
    try:
        with open(file_path, 'r') as file:
            lines = file.readlines()
        sections = []
        current_section = None
        current_section_lines = []
        for line in lines:
            if line.startswith("["):
                if current_section:
                    sections.append({
                        "name": current_section,
                        "lines": current_section_lines
                    })
                current_section = line
                current_section_lines = []
            else:
                current_section_lines.append(line)

        if current_section:
            sections.append({
                "name": current_section,
                "lines": current_section_lines
            })
        result_lines = []
        for section in sections:
            section_name = section["name"]
            section_lines = section["lines"]
            if section_name.startswith("[Application]"):
                result_lines.append(section_name)
                result_lines.append(section_lines[0])
                result_lines.append(section_lines[1])
                other_lines = section_lines[2:]
                priority_lines = (i for i, v in enumerate(other_lines) if "util-base" in v or "core" in v or "extensions" in v or "lz4" in v)
                for priority_line in priority_lines:
                    result_lines.append(other_lines[priority_line])
                    other_lines.remove(other_lines[priority_line])
                for other_line in other_lines:
                    result_lines.append(other_line)
            else:
                result_lines.append(section_name)
                for line in section_lines:
                    result_lines.append(line)
        with open(file_path, 'w') as file:
            file.writelines(result_lines)
    except FileNotFoundError:
        print("File not found")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python modify-classpath-mac.py <file_path>")
    else:
        file_path = sys.argv[1]
        reorder_lines(file_path)