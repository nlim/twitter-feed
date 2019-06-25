# Configure

Add file src/main/resources/application.conf with Twiiter API authentication components

```
consumer-key     = ""
consumer-secret  = ""
access-token     = ""
access-secret    = ""
```

# Run

## Start server

```
$ sbt run

```

## Obtain stats

`GET /stats` 

Example response shown below using Httpie (https://httpie.org): 

```
$ http GET localhost:8080/stats
HTTP/1.1 200 OK
Content-Length: 1244
Content-Type: application/json
Date: Tue, 25 Jun 2019 19:51:02 GMT

{
    "averageTweetsPerHour": 122400,
    "averageTweetsPerMinute": 2040,
    "averageTweetsPerSecond": 34,
    "percentTweetsWithEmojis": 21,
    "percentTweetsWithPhoto": 21,
    "percentTweetsWithUrl": 20,
    "topDomains": [
        "twitter.com",
        "du3a.org",
        "www.facebook.com",
        "youtu.be",
        "bit.ly",
        "www.youtube.com",
        "dlvr.it",
        "curiouscat.me",
        "jaywaninc.com",
        "www.binbir.tv"
    ],
    "topEmojis": [
        {
            "name": "SKATEBOARD",
            "short_name": "skateboard",
            "unified": "1F6F9"
        },
        {
            "name": "EMOJI MODIFIER FITZPATRICK TYPE-6",
            "short_name": "skin-tone-6",
            "unified": "1F3FF"
        },
        {
            "name": "NAZAR AMULET",
            "short_name": "nazar_amulet",
            "unified": "1F9FF"
        },
        {
            "name": null,
            "short_name": "keycap_star",
            "unified": "002A-FE0F-20E3"
        },
        {
            "name": "HEAVY BLACK HEART",
            "short_name": "heart",
            "unified": "2764-FE0F"
        },
        {
            "name": "BLACK HEART SUIT",
            "short_name": "hearts",
            "unified": "2665-FE0F"
        },
        {
            "name": null,
            "short_name": "male_sign",
            "unified": "2642-FE0F"
        },
        {
            "name": "WHEELCHAIR SYMBOL",
            "short_name": "wheelchair",
            "unified": "267F"
        },
        {
            "name": null,
            "short_name": "mountain",
            "unified": "26F0-FE0F"
        },
        {
            "name": "WHITE MEDIUM STAR",
            "short_name": "star",
            "unified": "2B50"
        }
    ],
    "topHashTags": [
        "LulaNaCadeia",
        "TeenChoice",
        "ChoiceInternationalArtist",
        "LulaLivreUrgente",
        "PuebloProtegidoEnRevolución",
        "AşkımızınAdı",
        "LaRoja",
        "fashion",
        "BoycottWayfair",
        "قطر"
    ],
    "totalTweets": 1020
}
```

