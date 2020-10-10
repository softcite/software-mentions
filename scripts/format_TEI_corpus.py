import argparse
import re

""" 
    Simple script to fix some default Java XML serialization issues. 
    This is normally useless, because the Java XML generation now includes its own corrected serialization
    (via the same regex). 
"""

if __name__ == "__main__":

    parser = argparse.ArgumentParser(
        description = "Fix XML serialization issues of a TEI XML file generated in Java")
    parser.add_argument("--tei-file", type=str, help="path to a TEI XML corpus file to format")
    parser.add_argument("--output", type=str, 
        help="path for reformatted TEI XML corpus file, default is the same directory as the input file, with an extension .reformatted")

    args = parser.parse_args()
    tei_file = args.tei_file
    output_path = args.output

    with open(tei_file) as xmldata:
        tei = xmldata.read()
        tei = re.sub(r'"\n( )*', '" ', tei)
        tei = re.sub(r'<p>\n( )*<ref', '<p><ref', tei)
        tei = re.sub(r'<p>\n( )*<rs', '<p><rs', tei)
        tei = re.sub(r'</ref>\n( )*<ref', '</ref> <ref', tei)
        tei = re.sub(r'</rs>\n( )*<rs ', '</rs> <rs ', tei)
        tei = re.sub(r' \n( )*<rs ', ' <rs ', tei)
        tei = re.sub(r'</rs>\n( )*<ref ', '</rs> <ref ', tei)
        tei = re.sub(r'</rs>\n( )*</p>', '</rs></p>', tei)
        tei = re.sub(r'</ref>\n( )*</p>', '</ref></p>', tei)
        tei = re.sub(r'</ref>\n( )*<rs ', '</ref> <rs ', tei)
        tei = re.sub(r'xmlns="" ', '', tei)
        xml_pretty_str = tei

    if output_path is not None:
        with open(output_path, "w") as xml_file:
            xml_file.write(xml_pretty_str)
    else:
        output_path += tei_file + ".reformatted"
        with open(output_path, "w") as xml_file:
            xml_file.write(xml_pretty_str)
