package com.example

// language=JSON
val nestedResponse = """
  {
    "data": {
      "viewer": {
        "__typename": "Viewer",
        "libraries": [
          {
            "__typename": "Library",
            "name": "library-1",
            "book":{
                "__typename": "Book",
                "id": "book=1",
                "name": "name-1",
                "year": 1991,
                "author": "John Doe"
              }
            }
        ]
      }
    }
  }
""".trimIndent()

// language=JSON
val nestedResponse_list = """
  {
    "data": {
      "viewer": {
        "__typename": "Viewer",
        "libraries": [
          {
            "__typename": "Library",
            "id": "library-1",
            "books": [
                  {
                      "__typename": "Book",
                      "id": "book-1",
                      "author": {
                        "__typename": "Author",
                        "id": "author-1"
                      }
                  }
              ]
            }
        ]
      }
    }
  }
""".trimIndent()
