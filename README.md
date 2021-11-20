Migrations:

    sbt flywayMigrate

Topics:
- updates
- control

Messages data:
```sql
select t.id, author, text, created_on, processed_on 
from texts t left join processed_texts pt on t.id = pt.id;
```
