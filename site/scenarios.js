/* Narrative snapshots of the Java sources, not a running backend. Text pairs: English, Chinese. */
const T = (en, zh) => [en, zh];
const step = (title, copy, active, flows, state, source) => ({ title, copy, active, flows, state, source });
const authSource = 'src/main/java/com/tszkinyu/performancelab/auth/AuthenticationService.java';
const querySource = 'src/main/java/com/tszkinyu/performancelab/events/CustomerEventSql.java';
globalThis.DEMO = {
    name:'Performance Lab', repo:'java-backend-performance-lab', revision:'45f1cb5586701a9f45da58edad63869d09f1b1be',
    label:T('JAVA BACKEND PERFORMANCE LAB','JAVA 后端性能实验室'),
    title:T('Less work. <span>Faster responses.</span>','少做重复工作，<span>更快响应。</span>'),
    intro:T('Follow a request. See where the work disappears. Then inspect the measured result.','跟随一次请求，看清优化减少了哪些工作，再对照真实测量结果。'),
    tech:'Java 21 / Spring Boot / PostgreSQL / Gatling',
    boundary:T('A source-based animation, not a live benchmark. Results are committed local measurements. Auth uses a synthetic 20 ms creation delay; sessions live in one process. Animation time is illustrative.','动画依据源码，不是实时压测。数字来自已提交的本地实验。认证包含 20 ms 人工创建延迟，会话只存于单进程。动画时间仅为示意。'),
    scenarios: {
      auth: {
        name:T('Session reuse','会话复用'), title:T('Two requests. Two different paths.','相同的请求，不同的处理路径。'), kind:'auth',
        lanes:[T('BASELINE · CREATE EVERY TIME','基线 · 每次重新创建'),T('OPTIMIZED · REUSE UNTIL EXPIRY','优化 · 未过期就复用')],
        nodes:[['a0',T('Client','客户端'),'clientId: demo'],['a1',T('REST API','REST API'),'/auth/baseline'],['a2',T('New session','创建新会话'),T('synthetic delay','人工模拟延迟')],['a3',T('Response','响应'),'reused: false'],['b0',T('Same client','同一客户端'),'clientId: demo'],['b1',T('REST API','REST API'),'/auth/session-reuse'],['b2',T('Session store','会话存储'),'compute(clientId)'],['b3',T('Response','响应'),T('same ID on hit','命中时返回相同 ID')]],
        footer:T('In-memory ConcurrentHashMap · TTL: 30 s by default','内存 ConcurrentHashMap · 默认 TTL：30 秒'),
        steps:[
          step(T('Start with the same client.', '从同一个客户端开始。'),T('Both endpoints validate clientId. The baseline creates a session on every call; the optimized path first checks its process-local session store.', '两个接口都会验证 clientId。基线每次都创建会话；优化路径先检查进程内的会话存储。'),['a0','a1','b0','b1'],[['a0','a1'],['b0','b1']],[T('Request','请求'),T('First visit','首次访问')],authSource),
          step(T('The first visit still does the work.', '第一次访问，仍然需要创建。'),T('The baseline creates a new UUID and expiry. The reuse path has no entry yet, so compute creates and stores a session too. A cache miss is not free.', '基线生成新的 UUID 与过期时间。复用路径尚无缓存，compute 同样创建并保存会话。缓存未命中仍有创建成本。'),['a2','b2'],[['a1','a2'],['b1','b2']],[T('Reuse store','会话存储'),T('Miss → create','未命中 → 创建')],authSource),
          step(T('Both return a new session.', '两条路径都返回新会话。'),T('The response contains clientId, session ID, expiresAt and reused. Both first responses report reused: false. Only the reuse path keeps the session for later calls.', '响应包含 clientId、会话 ID、expiresAt 和 reused。首次响应都为 reused: false，只有优化路径会保存会话以供后续使用。'),['a3','b3'],[['a2','a3'],['b2','b3']],[T('First response','首次响应'),'reused: false'],authSource),
          step(T('Now the same client returns.', '同一个客户端再次请求。'),T('Before the TTL expires, repeat the same request. The baseline starts creation again. The reuse path finds an existing, active session in compute.', '在 TTL 到期前，再发送相同请求。基线再次开始创建；复用路径在 compute 中找到仍有效的已有会话。'),['a1','a2','b1','b2'],[['a0','a1'],['b0','b1']],[T('Request','请求'),T('Repeat within TTL','有效期内重复')],authSource),
          step(T('Skip creation. Keep the session.', '跳过创建，复用原会话。'),T('The baseline pays the synthetic creation delay again. The active-session branch returns the existing ID immediately and reports reused: true. The animation slows both paths for explanation.', '基线再次承担人工创建延迟。有效会话分支直接返回原 ID，并标记 reused: true。动画为了讲解，放慢了两条路径。'),['a2','a3','b2','b3'],[['a1','a2'],['a2','a3'],['b2','b3']],[T('Repeat response','重复请求响应'),'reused: true'],authSource),
          step(T('Expiry brings creation back.', '过期后，再次创建。'),T('A session is active only when expiresAt is after now. At or after expiry, compute replaces it with a fresh session; reused becomes false again. The cache does not extend TTL on a hit.', '仅当 expiresAt 晚于当前时间时会话才有效。到期后，compute 会替换为新会话，reused 重新变为 false。缓存命中不会延长 TTL。'),['b2','b3'],[['b1','b2'],['b2','b3']],[T('Expired response','过期后响应'),'reused: false'],authSource),
          step(T('Measure the work you removed.', '用测量验证省下的工作。'),T('The committed Gatling run reports 34 → 6 ms mean latency, and 37 → 14 ms p95. Each case measured 1,500 requests. These are local synthetic results, not production capacity.', '仓库中的 Gatling 实验记录：平均延迟 34 → 6 ms，p95 为 37 → 14 ms。每组测量 1,500 次请求。这是本地合成实验，不代表生产容量。'),['a3','b3'],[],[T('Measured requests','每组请求'),'1,500 / case'],'BENCHMARKS.md'),
        ],
        evidence:[{label:T('Mean response time','平均响应时间'),before:'34',after:'6',unit:'ms',note:T('Local Gatling run · 100 requests/s','本地 Gatling 实验 · 100 请求/秒')},{label:T('p95 response time','p95 响应时间'),before:'37',after:'14',unit:'ms',note:T('5 s warm-up · 15 s measurement / case','每组预热 5 秒 · 测量 15 秒')}],
      },
      index: {
        name:T('Database index','数据库索引'),title:T('One query. Less database work.','同一条查询，更少数据库工作。'),kind:'index',
        lanes:[T('BEFORE · SEQUENTIAL SCAN + SORT','之前 · 顺序扫描与排序'),T('AFTER · COMPOSITE INDEX','之后 · 复合索引')],
        nodes:[['a0',T('Query','查询'),'tenant + type + time'],['a1',T('Seq scan','顺序扫描'),T('filter table rows','过滤表中数据')],['a2',T('Sort & merge','排序与合并'),'occurred_at DESC'],['a3',T('100 rows','100 条记录'),'LIMIT 100'],['b0',T('Same query','相同查询'),'tenant + type + time'],['b1',T('Index scan','索引扫描'),'tenant_id, event_type'],['b2',T('Ordered reads','按序读取'),'occurred_at DESC'],['b3',T('100 rows','100 条记录'),'LIMIT 100']],
        footer:T('1,000,000 generated rows · cells represent work, not individual rows','1,000,000 条生成数据 · 方格代表工作量，不代表逐条记录'),
        steps:[
          step(T('Ask a specific question.', '查询一个明确的范围。'),T('Find tenant 42’s PURCHASE events since January 1, 2025. Return the newest 100. The before and after cases execute the exact same query.', '查找租户 42 自 2025 年 1 月 1 日起的 PURCHASE 事件，返回最新的 100 条。优化前后执行完全相同的查询。'),['a0','b0'],[],[T('Dataset','数据集'),'1,000,000 rows'],querySource),
          step(T('Without an index, examine the table.', '没有索引，就要扫描表。'),T('The recorded baseline plan uses parallel sequential scans, filtering rows across the table. LIMIT 100 does not remove the cost of finding and ordering matching events.', '基线执行计划使用并行顺序扫描，对表中数据做过滤。LIMIT 100 并不能省去查找和排序匹配事件的成本。'),['a1'],[['a0','a1']],[T('Before plan','优化前计划'),'Seq Scan'], 'benchmarks/results/postgresql-18.4/index-comparison.json'),
          step(T('Match the index to the question.', '按查询条件设计索引。'),T('The composite index starts with tenant_id and event_type, then occurred_at DESC. Equality filters narrow the range; the timestamp supplies the requested order.', '复合索引依次包含 tenant_id、event_type 和 occurred_at DESC。等值条件缩小查找范围，时间字段提供所需顺序。'),['b1'],[['b0','b1']],[T('Index order','索引顺序'),'tenant → type → time'],'src/main/resources/db/migration/V2__index_customer_events_lookup.sql'),
          step(T('Sort candidates, or read in order.', '先排序候选项，或直接按序读取。'),T('The baseline sorts candidates and merges worker results. The indexed plan reads matching entries in descending time order, fetching table rows as needed. It is an Index Scan, not an index-only scan.', '基线对候选项排序，并合并并行结果。索引方案按时间倒序读取匹配项，并按需读取表记录。这是 Index Scan，不是仅索引扫描。'),['a2','b2'],[['a1','a2'],['b1','b2']],[T('After plan','优化后计划'),'Index Scan'],querySource),
          step(T('Stop after the newest 100.', '拿到最新 100 条后停止。'),T('Both paths return 100 rows. The index allows the database to stop once enough ordered matches are found. The returned data is unchanged; the path to it is shorter.', '两条路径都返回 100 条。索引让数据库在获取足够的有序匹配项后停止。结果相同，找到结果需要的工作更少。'),['a3','b3'],[['a2','a3'],['b2','b3']],[T('Result count','返回数量'),'100 / case'],querySource),
          step(T('Inspect the recorded query plans.', '对照保存的执行计划。'),T('Five measured runs after two warm-ups gave medians of 53.489 ms and 0.040 ms. Hardware, data distribution and cache state affect this local result.', '两次预热后，各测量五次，中位数为 53.489 ms 与 0.040 ms。硬件、数据分布和缓存状态都会影响这一本地结果。'),['a3','b3'],[],[T('Measurement','测量方式'),'EXPLAIN ANALYZE'],'benchmarks/results/postgresql-18.4/index-comparison.json'),
        ],
        evidence:[{label:T('Median query time','查询时间中位数'),before:'53.489',after:'0.040',unit:'ms',note:T('2 warm-ups + 5 measured runs / case','每组预热 2 次 + 测量 5 次')},{label:T('Dataset size','数据集大小'),value:'1,000,000',unit:T('rows','条'),note:T('Deterministic, generated PostgreSQL data','PostgreSQL 生成的确定性数据')}],
      },
    },
  };
