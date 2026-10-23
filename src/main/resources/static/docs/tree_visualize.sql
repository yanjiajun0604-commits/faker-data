-- 树形结构可视化：按层级缩进展示
WITH RECURSIVE tree_view AS (
    -- 根节点（parentid IS NULL）
    SELECT
        id,
        name,
        code,
        parentid,
        0 AS level,
        CAST(code AS CHAR(200)) AS path
    FROM fakertest.tree_table
    WHERE parentid IS NULL

    UNION ALL

    -- 递归子节点
    SELECT
        t.id,
        t.name,
        t.code,
        t.parentid,
        tv.level + 1,
        CONCAT(tv.path, ' → ', t.code) AS path
    FROM fakertest.tree_table t
    INNER JOIN tree_view tv ON t.parentid = tv.id
)
SELECT
    CONCAT(REPEAT('    ', level), code) AS 树形结构,
    name AS 节点名称,
    id AS UUID,
    parentid AS 父节点UUID,
    path AS 完整路径
FROM tree_view
ORDER BY path;



