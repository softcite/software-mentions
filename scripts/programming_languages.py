#!/usr/bin/env python

# Originally from https://gist.github.com/turicas/d5f8ce3ceb99f43a11b1e4e7fb2a2bf9
# 1) updated to work on new version of the Wikipedia page
# 2) extended to Wikidata entities, via Wikidata web service

from io import BytesIO
from urllib.parse import urljoin

import requests
import rows
import json

PROGRAMMING_LANGUAGES_URL = \
        'https://en.wikipedia.org/wiki/List_of_programming_languages'

blacklist = ['DinkC']

def _convert_row(row):
    url = urljoin('https://en.wikipedia.org/', row.url)
    if 'redlink=1' in url:  # Wikipedia article does not exist
        url = None
    if 'ISO/' in url:
        # we have an ISO standard for a programming language, but not a programming language per se 
        url = None
        return {}

    # get Wikidata ID from entity-fishing
    wikidata_id = None

    if url is not None:
        # get page title
        ind = url.rfind("/")
        if ind != -1:
            title = url[ind+1:]

        if not "#" in title:
            url_wikidata_id = "https://www.wikidata.org/w/api.php?action=wbgetentities&sites=enwiki&titles=" + title + "&normalize=1&format=json"
            #print(url_wikidata_id)
            resp = requests.get(url=url_wikidata_id)
            if resp.status_code == 200:
                data = resp.json()
                if data["success"] == 1:
                    # even when success is 1, the request might have fail, the key is then -1            
                    for key in data["entities"]:
                        if key != "-1":
                            wikidata_id = data["entities"][key]["id"]
                        break

    result = {'name': row.name,
            'wikipedia_url': url, 
            'wikidata_id': wikidata_id}

    return result


def download_programming_languages(filename):

    response = requests.get(PROGRAMMING_LANGUAGES_URL)

    # XPath is as follow: /html/body/div[3]/div[3]/div[5]/div[1]/div[2]/ul/li[1]/a

    table = rows.import_from_xpath(
            BytesIO(response.content),
            encoding=response.encoding,
            rows_xpath='//*[@id="mw-content-text"]/div/div/ul/li/a',
            fields_xpath={'name': './/text()',
                          'url': './/@href', })
    table = rows.import_from_dicts(map(_convert_row, table))

    rows.export_to_csv(table, filename)

    return table


if __name__ == '__main__':
    download_programming_languages('programming-languages.csv')
