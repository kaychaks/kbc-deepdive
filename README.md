### Domain Specific KBC

Creating a knowledge base for a particular business domain e.g. insurance using [DeepDive](http://deepdive.stanford.edu)

## Preparation

- get the input data (e.g. `transcripts.tsv.tar.gz`) from S3 (or any other remote storage location), place inside `input` folder
    - DeepDive's built-in process `process/init/app` will use `input/init.sh` to take care of extracting the zip file and also making the file ready for processing
- make necessary path changes to the `docker-compose` file
- run the `docker-compose` file which is going to setup both the DeepDive & Postgres containers

```
docker-compose up -d
```

_follow the instructions mentioned in the `deepdive-docker-setup` folder to create the DeepDive docker image to be used in the docker-compose.yml_

## DeepDive Workflow

_Please refer to [DeepDive's documentation](http://deepdive.stanford.edu/#documentation) for extensive details on each command listed below_.

> `deepdive compile` command should be executed before running any of the `do` commands mentioned below and after any changes are made in `app.ddlog` file.

- Loading the transcripts in the DB (if not loaded before). Process of transforming an unstructured chat corpus to transcripts that can be loaded is described [below](#idp)

```
deepdive do transcripts
```

- In case the transcripts are loaded before, following commands are required to reload the new data from a fresh transcripts file

```
deepdive redo process/init/app

deepdive redo transcripts
```

- Fetching all the distinct agents from the transcripts

```
deepdive do agents
```

- Transcripts are converted to turn based chat conversation
```
deepdive do turns
```
    - Each transcript data is turned into turn based chat conversation so that a fine grained analysis can be done
    
    ```
    turns(
        trans_id uuid,
        agent_or_user text,
        name text,
        chat text,
        turn_id int,
        reply_to int
    ).
    ```
    - Turn data is generated via an user defined function (UDF). The UDF here is part of a Scala script following the [Ammonite](http://www.lihaoyi.com/Ammonite/) project. Function takes relevant `id` & `chat` column data and process them to generate data matching Turn data structure as mentioned above
    
    ```
    function turn_gen over (
            id uuid,
            chat text
        ) returns rows like turns
        implementation "udf/turn_gen.sc" handles tsv lines.

    turns += turn_gen(id, chat) :-
          transcripts(id, _, _, _, chat).
    ```
    
- Adding NLP markups to Turn data
    - This step will split turn data into sentences and their component tokens. Additionally other markups like lemmas, part-of-speech tags, named entity recognition tags, and a dependency parse of the sentence will be generated. The output schema looks like this   
    
    ```
    sentences(    
        doc_id text,
        sentence_index int,    
        sentence_text  text,
        tokens         text[],
        lemmas         text[],
        pos_tags       text[],
        ner_tags       text[],
        doc_offsets    int[],
        dep_types      text[],
        dep_tokens     int[]
    ).
    ```
    
    - An UDF will use DeepDive's wrapper project on Stanford's CoreNLP parser to produce required markups taking `id` & `chat` text from turn data. Additional checks on the turn data fetch are put to take care of null data & malformed HTML issues which Stanford NLP rejects to parse.
    
    ```
    function nlp_chat over (
        id text,
        chat text
    ) returns rows like sentences
    implementation "udf/nlp_chat.sh" handles tsv lines.

    sentences += nlp_chat((trans_id :: text) || "_" || turn_id, chat):-
        turns(trans_id,_,_,chat,turn_id,_), [chat != "";chat IS NOT NULL],!chat LIKE "%<p align=\"center\">Chat Origin:%".
    ```
    
    - execution of above process happens with following command
    
    ```
    deepdive do sentences
    ```


## Querying

`deepdive query` command can be used to take a look at loaded data e.g.

- show count of unique agents mentioned the input corpus

```
deepdive query count(n) ?- agents(n).
```

- show first 5 agents with their departments & respective chat transcript

```
deepdive query agent, dep, chat | 5 ?- transcripts(_, _, dep, agent, chat).
```

- show top 10 biggest chat conversation ids (along with number of turns)

```
deepdive query 'id,@order_by("DESC") COUNT(id) | 10 ?- turns(id,_,_,_,_,_).'
```

For more information on DeepDive's query syntax and features, check out [this documentation](http://deepdive.stanford.edu/ops-data#running-queries-against-the-database)

## <a name="idp"></a>Input Data Processing

Raw input data corpus (in this case chat transcripts) is parsed and processed into a structured content as modelled in the `transcripts` relation of `app.ddlog` via [kbc-data-processing](https://github.com/kaychaks/kbc-data-processing) project. If any model changes are made to the `transcripts` relation here then subsequent changes are required to made in the mentioned project.

Also to process a new input data corpus the same `kbc-data-processing` project need to be run again. For relevant information on how to run the processing task please refer it's [documentation](https://github.com/kaychaks/kbc-data-processing.git).

## Initialisation Process (process/init/app)
This is a [built-in process of DeepDive](http://deepdive.stanford.edu/ops-execution#built-in-data-flow-nodes). Here, during this process, we do
- Get rid of the first blank line of transcripts input file
- Installing & setting up Ammonite
- Pre-compile any UDF Scala scripts via Ammonite so that the actual UDF execution becomes faster
- Install and setup [a fork](https://github.com/kaychaks/bazaar.git) of DeepDive's wrapper of Stanford CoreNLP [bazaar/parser](https://github.com/HazyResearch/bazaar/tree/master/parser). The fork resolves [a bug](https://github.com/kaychaks/bazaar/commit/af1ae6ba9afa1719bf09a30af49b5e1afb0cdd98) that prevents NLP parsed data to be copied inside Postgres DB.

Not part of the init script but this script need to be sourced before running any workflow commands which adds `${HOME}/local/util` folder to the global `$PATH`. It contains some useful executables (like `tsv2json` & `jq`) required during DeepDive workflow
```
source input/env.sh
```
## Troubleshooting
- `apt-get update` is not happening
  - create an `apt.conf` file inside `/etc/apt/` folder having following syntax
  
  ```
  Acquire::http::proxy "<YOUR_PROXY>";
  Acquire::https::proxy "<YOUR_PROXY>";
  ```
- getting some kind of certificate error during JVM operation like fetching artifacts from `sbt`
  - add the MITM certificate to `/usr/local/share/ca-certificates/` folder
  - and then execute the following commands
  
  ```
  $ /usr/sbin/update-ca-certificates -f
  $ /usr/bin/keytool -import -noprompt -trustcacerts -file "/usr/local/share/ca-certificates/<YOUR_CERTIFICATE>" -keystore "/usr/lib/jvm/java-1.8.0-openjdk-amd64/jre/lib/security/cacerts" -storepass changeit
  ```
  
- if for some reason scala scripts based UDF fails first time when a relevant DeepDive process is run, try running the same again. It might happen that the initial failure was due to script compilation message being printed to `stdout` which DeepDive took as actual data for Postgres
- NLP parsing step might fail because of lack of compute resources available in local machine. In that case it's better to do the same computation in some cloud / external server & import the parsed data like this (deepdive load could load bz2 zipped files as well)

```
deepdive load sentences sentences_parsed.tsv.bz2
```

## Latent Semantic Analysis

Checkout the `lsa` folder for more information on how Scala & Spark is being used to do the same.
