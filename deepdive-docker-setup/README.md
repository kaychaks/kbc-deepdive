Required files to do Docker setup of DeepDive with a linked Postgres container

## Instructions
- `Dockerfile` contains the required steps to install DeepDive. Therefore, it needs to be build and tagged for the `docker-compose` to use. Alternatively, `docker-compose.yml` can be modified to build the container using `docker-compose`

  -  To build the containers using `docker-compose build`, make the following modifications to `docker-compose.yml` (_assuming that the `Dockerfile` is in the same folder as `docker-compose.yml`_)

  ```
  deepdive:    
      build: .
      volumes:
        ...
  ```
- `docker-compose.yml` contains the required services to start couple of containers - `deepdive` & `postgres` and link them. Command to run and start the containers

  ```
  docker-compose up -d
  ```

- `deepdive` service has one volume available for mount - `/root/apps`. This is required so that any host system location could be used to place `DeepDive` apps & make it available inside the container.

- Similarly, `postgres` service has the `/var/lib/postgresql/data` directory also available for mount to store the DB data in the host system

- `postgres` service modifies the password for the default `postgres` user account to `deepdive`. Additionally, the default database that will be created as the container starts has been changed to `deepdive_docker`

- `postgres` service exposes the port `5432`. So the `DeepDive` apps placed inside the container for `deepdive` service could use url for `db.url` file like this

  ```
  postgresql://postgres:deepdive@db:5432/deepdive_docker
  ```

## Further References
- [Dockerfile file reference](https://docs.docker.com/engine/reference/builder/#/cmd)

- [docker-compose file reference](https://docs.docker.com/compose/compose-file/#/entrypoint)

- [How DeepDive applications are typically developed](http://deepdive.stanford.edu/development-cycle)
