SELECT *
FROM pg_stat_database
WHERE datname like :databasename
