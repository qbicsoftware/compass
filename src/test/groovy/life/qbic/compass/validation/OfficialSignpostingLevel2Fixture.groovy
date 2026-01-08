package life.qbic.compass.validation

import java.nio.charset.StandardCharsets

trait OfficialSignpostingLevel2Fixture {
    static String OFFICIAL_LEVEL2_LINKSET_JSON = '''
{
  "linkset": [
    {
      "anchor": "https://example.org/page/7507",
      "cite-as": [
        {
          "href": "https://doi.org/10.5061/dryad.5d23f"
        }
      ],
      "type": [
        {
          "href": "https://schema.org/ScholarlyArticle"
        },
        {
          "href": "https://schema.org/AboutPage"
        }
      ],
      "author": [
        {
          "href": "https://orcid.org/0000-0002-1825-0097"
        },
        {
          "href": "https://isni.org/isni/0000002251201436"
        }
      ],
      "item": [
        {
          "href": "https://example.org/file/7507/1",
          "type": "application/pdf"
        },
        {
          "href": "https://example.org/file/7507/2",
          "type": "text/csv"
        },
        {
          "href": "https://gitmodo.io/johnd/ct.zip",
          "type": "application/zip"
        }
      ],
      "describedby": [
        {
          "href": "https://example.org/meta/7507/bibtex",
          "type": "application/x-bibtex"
        },
        {
          "href": "https://doi.org/10.5061/dryad.5d23f",
          "type": "application/vnd.datacite.datacite+json"
        },
        {
          "href": "https://example.org/meta/7507/citeproc",
          "type": "application/vnd.citationstyles.csl+json"
        }
      ],
      "license": [
        {
          "href": "https://spdx.org/licenses/CC-BY-4.0"
        }
      ]
    },
    {
      "anchor": "https://example.org/file/7507/1",
      "collection": [
        {
          "href": "https://example.org/page/7507",
          "type": "text/html"
        }
      ]
    },
    {
      "anchor": "https://example.org/file/7507/2",
      "collection": [
        {
          "href": "https://example.org/page/7507",
          "type": "text/html"
        }
      ],
      "type": [
        {
          "href": "https://schema.org/Dataset"
        }
      ]
    },
    {
      "anchor": "https://gitmodo.io/johnd/ct.zip",
      "collection": [
        {
          "href": "https://example.org/page/7507",
          "type": "text/html"
        }
      ],
      "type": [
        {
          "href": "https://schema.org/SoftwareSourceCode"
        }
      ]
    },
    {
      "anchor": "https://doi.org/10.5061/dryad.5d23f",
      "describes": [
        {
          "href": "https://example.org/page/7507",
          "type": "text/html"
        }
      ]
    },
    {
      "anchor": "https://example.org/meta/7507/bibtex",
      "describes": [
        {
          "href": "https://example.org/page/7507",
          "type": "text/html"
        }
      ]
    }
  ]
}
'''

    static InputStream officialJsonStream() {
        return new ByteArrayInputStream(OFFICIAL_LEVEL2_LINKSET_JSON.getBytes(StandardCharsets.UTF_8))
    }
}
