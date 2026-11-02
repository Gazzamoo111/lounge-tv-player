-- Lounge TV canonical Live catalogue v3 proposal (READ ONLY)
--
-- This query does not create, update, or delete database objects. It derives a
-- proposed customer catalogue from the current BEST inventory while retaining
-- useful sources as backend-only backups/events.
--
-- Locked category numbers from Gary's brief were alphabetical row numbers.
-- They are resolved below to stable BEST category_id values.

with
params as (
  select current_date as as_of_date, current_date + 14 as through_date
),
category_routes(category_id, top_category, subcategory, policy) as (
  values
    -- New Zealand and Australia. BEST NZ TV category 1007 is intentionally
    -- absent in v3; the separate Freeview playlist is deferred.
    ('2401','New Zealand','Sports','full'),
    ('1606','New Zealand','Sports','full'),
    ('384','Australia','TV','full'),
    ('2231','Australia','TV','full'),

    -- UK/US permanent sports groups. Package rows are selected below only when
    -- they are permanent networks rather than numbered game/event feeds.
    ('401','Sports','UK Sports','full'),
    ('1661','Sports','UK Sports','full'),
    ('680','Sports','Networks & Talk','full'),
    ('2232','Sports','Networks & Talk','full'),
    ('675','Sports','NFL','permanent_sports'),
    ('676','Sports','NBA & WNBA','permanent_sports'),
    ('677','Sports','NHL','permanent_sports'),
    ('804','Sports','MLB','permanent_sports'),
    ('1073','Sports','Football','permanent_sports'),
    ('2076','Sports','NBA & WNBA','permanent_sports'),
    ('1751','Sports','NBA & WNBA','permanent_sports'),
    ('1308','Sports','Football','permanent_sports'),
    ('1580','Sports','Motorsport','motorsport_primary'),

    -- Entertainment and core TV
    ('1025','Entertainment','UK TV','full'),
    ('521','Entertainment','UK TV','full'),
    ('1665','Entertainment','UK TV','full'),
    ('2213','Entertainment','UK Now TV','full'),
    ('672','Entertainment','US TV','full'),
    ('1611','Entertainment','US TV','full'),
    ('519','Entertainment','UK TV','core_uk'),
    ('2540','Entertainment','US TV','full'),

    -- Movies
    ('522','Movies','UK Movies','full'),
    ('1659','Movies','UK Movies','full'),
    ('1350','Movies','Sky Store','full'),
    ('2151','Movies','UK Movies','full'),
    ('2148','Movies','UK Movies','full'),
    ('1642','Movies','US Movies','full'),
    ('674','Movies','US Movies','full'),

    -- Kids
    ('520','Kids & Family','TV','full'),
    ('1660','Kids & Family','TV','full'),
    ('666','Kids & Family','TV','full'),

    -- Documentaries
    ('524','Documentaries & Lifestyle','Documentaries','full'),
    ('1664','Documentaries & Lifestyle','Documentaries','full'),

    -- Streaming
    ('2247','Streaming','Apple TV+','full'),
    ('1515','Streaming','Disney+','full'),
    ('1345','Streaming','HBO Max','full'),
    ('1344','Streaming','Hulu','full'),
    ('2248','Streaming','Netflix','full'),
    ('2285','Streaming','Netflix','full'),
    ('1347','Streaming','Paramount+','full'),
    ('1346','Streaming','Peacock','full'),
    ('1211','Streaming','Pluto TV','full'),
    ('1127','Streaming','Prime Video','full'),
    ('2246','Streaming','Prime Video','full'),

    -- v3 removes Action & Adventure (2294) but retains the other locked groups.
    ('2290','24/7','Cartoons','full'),
    ('2292','24/7','Kids & Family','full'),
    ('765','24/7','Movies & Series','full'),
    ('2244','24/7','OnePlay','full'),
    ('615','24/7','Movies & Series','full'),
    ('2287','24/7','Movies & Series','wwe_247_only'),

    -- PPV groups. Only dated current/upcoming events with an approved identity
    -- can enter the customer proposal; every other source remains backend-only.
    ('1523','PPV','UK Live Events','current_ppv'),
    ('2489','PPV','UK Live Events','current_ppv'),
    ('1528','PPV','UK Live Events','current_ppv'),
    ('614','PPV','UK Live Events','current_ppv'),
    ('1592','PPV','UFC / MMA','current_ppv'),
    ('1568','PPV','US Live Events','current_ppv'),
    ('2219','PPV','AU Live Events','current_ppv'),

    -- High-value channels hidden inside large raw banks.
    ('667','Entertainment','Business & Finance','business_only'),
    ('905','Entertainment','Business & Finance','business_only'),
    ('2183','Entertainment','Business & Finance','high_value_only'),
    ('2186','Entertainment','Business & Finance','high_value_only')
),
event_category_ids(category_id) as (
  values
    ('668'),('860'),('1073'),('1117'),('1118'),('1124'),('1165'),
    ('1307'),('1308'),('1309'),('1322'),('1334'),('1367'),('1503'),
    ('1504'),('1505'),('1521'),('1523'),('1527'),('1528'),('1532'),
    ('1559'),('1560'),('1561'),('1564'),('1566'),('1567'),('1568'),
    ('1569'),('1570'),('1571'),('1572'),('1579'),('1580'),('1581'),
    ('1590'),('1591'),('1592'),('1593'),('1596'),('1612'),('1621'),
    ('1622'),('1623'),('1629'),('1631'),('1647'),('1655'),('1656'),
    ('1658'),('1753'),('1785'),('1795'),('1857'),('1900'),('1916'),
    ('1982'),('1999'),('2021'),('2022'),('2170'),('2175'),('2176'),
    ('2177'),('2178'),('2187'),('2202'),('2204'),('2219'),('2222'),
    ('2223'),('2224'),('2243'),('2258'),('2270'),('2332'),('2336'),
    ('2403'),('2420'),('2424'),('2425'),('2426'),('2436'),('2469'),
    ('2489'),('2495'),('2496'),('2499'),('2505'),('2511'),('2528')
),
netflix_priority(title, priority) as (
  values
    ('stranger things',1),('wednesday',2),('squid game',3),('black mirror',4),
    ('the crown',5),('bridgerton',6),('ozark',7),('narcos',8),
    ('the witcher',9),('house of cards',10),('orange is the new black',11),
    ('cobra kai',12),('lucifer',13),('vikings',14),('the last kingdom',15),
    ('the gentlemen',16),('the queens gambit',17),('queen charlotte a bridgerton story',18),
    ('la casa de papel',19),('the umbrella academy',20),('the sandman',21),
    ('the night agent',22),('the diplomat',23),('the lincoln lawyer',24),
    ('mindhunter',25),('dark',26),('arcane',27),('one piece',28),
    ('avatar the last airbender',29),('13 reasons why',30),('3 body proplem',31),
    ('alice in borderland',32),('all of us are dead',33),('altered carbon',34),
    ('anne with an e',35),('atypical',36),('black doves',37),
    ('blue eye samurai',38),('bojack horseman',39),
    ('chilling adventures of sabrina',40),('cyberpunk edgerunners',41),
    ('dead to me',42),('designated survivor',43),('emily in paris',44),
    ('fubar',45),('ginny and georgia',46),('godless',47),
    ('the haunting of hill house',48),('the haunting of bly manor',49),
    ('lost in space',50),('lupin',51),('manifest',52),
    ('marvels daredevil',53),('marvels the punisher',54),
    ('midnight mass',55),('outer banks',56),('riverdale',57),
    ('sense8',58),('sweet tooth',59),('virgin river',60)
),
source_health as (
  select provider_stream_id,
         max(health_score) health_score,
         max(quality_score) quality_score,
         max(last_success_at) last_success_at
  from lounge_core.live_sources
  group by provider_stream_id
),
base as (
  select
    i.provider_item_id,
    i.name source_name,
    i.category_id,
    c.category_name provider_category,
    nullif(i.epg_channel_id,'') epg_id,
    nullif(i.artwork_url,'') artwork_url,
    i.last_seen_at,
    coalesce(h.health_score,0) health_score,
    coalesce(h.quality_score,0) measured_quality_score,
    h.last_success_at,
    r.top_category route_top,
    r.subcategory route_sub,
    r.policy route_policy,
    (e.category_id is not null) category_is_event_pool,
    case
      when i.category_id in ('1668','1865') then true
      when i.name ~* '(^|[[:space:]])(ES|PT|BR|LATAM|LATINO)[[:space:]]*\|' then true
      when i.name ~* '(DEPORTES|ESPA[NÑ]OL|PORTUGU[EÊ]S|EN ESPA[NÑ]OL|EM PORTUGU[EÊ]S)' then true
      when i.name ~* '\((ES|PT|BR)\)' then true
      else false
    end is_foreign_language,
    case
      when i.name ~* '(OFFLINE|NO SIGNAL|NOT WORKING|DEAD FEED|PLACEHOLDER|COMING SOON)' then true
      when btrim(i.name) ~ '^[-=_ ]+$' then true
      when btrim(i.name) ~ '^#+.*#+$' then true
      when i.name ~* '(^|\|[[:space:]]*)CM[[:space:]]+(AMAZON|APPLE TV PLUS|DISNEY PLUS|NETFLIX)[[:space:]]+[0-9]+([[:space:]]|$)' then true
      else false
    end is_obviously_broken,
    case
      when i.name ~* '^(CHANNEL|FEED|STREAM|EVENT|PPV)[[:space:]_-]*[0-9]+$' then true
      when i.name ~* '^(LIVE[[:space:]]+)?EVENT[[:space:]_-]*[0-9]+([[:space:]]|$)' then true
      else false
    end is_generic_numbered_feed,
    case
      when i.name ~* '(TAB[[:space:]]+TRACKSIDE|SKY[[:space:]]+RACING|RACING\.COM|RACING[[:space:]]+UK|SKY[[:space:]]+SPORTS[[:space:]]+RACING|RACING[[:space:]]+AMERICA|FLORACING|EQUIDIA[[:space:]]+RACING|HORSE[[:space:]]+RACING)' then true
      when i.name ~* '(^|\|[[:space:]]*)RACING([[:space:]]+(HD|FHD|SD|RAW|ᴿᴬᵂ))?[[:space:]]*$' then true
      else false
    end is_dedicated_racing,
    case
      when i.category_id in ('672','2540')
       and i.name ~* '(CNN|FOX[[:space:]]+NEWS|NBC[[:space:]]+NEWS|ABC[[:space:]]+NEWS|CBS[[:space:]]+NEWS|BBC[[:space:]]+NEWS|NEWSNATION|NEWSMAX|MSNBC|MS[[:space:]]+NOW|SCRIPPS[[:space:]]+NEWS|NEWSY|C-SPAN|CSPAN|OANN|ONE[[:space:]]+AMERICA|RT[[:space:]]+NEWS|SKY[[:space:]]+NEWS)'
       and i.name !~* '(ESPNEWS|SPORTS[[:space:]]+NEWS|FOX[[:space:]]+BUSINESS|CNBC|BLOOMBERG|CHEDDAR)'
      then true else false
    end is_us_news,
    case
      when i.category_id in ('672','2540') and (
        i.name ~* '(A3[[:space:]]+NEW[[:space:]]+YORK|ABC7[[:space:]]+NEW[[:space:]]+YORK|NEW[[:space:]]+YORK,[[:space:]]+NY)'
        or i.name ~* '(^|\|[[:space:]])(MY27|TXA21)[[:space:]]+[A-Z]{4}'
      ) then true else false
    end is_us_affiliate,
    case
      when substring(i.name from '([0-9]{4}-[0-9]{2}-[0-9]{2})') is not null
      then to_date(substring(i.name from '([0-9]{4}-[0-9]{2}-[0-9]{2})'),'YYYY-MM-DD')
      else null
    end event_date
  from lounge_sync.best_inventory_items i
  join lounge_sync.best_categories c
    on c.kind=i.kind and c.category_id=i.category_id
  left join source_health h on h.provider_stream_id=i.provider_item_id
  left join category_routes r on r.category_id=i.category_id
  left join event_category_ids e on e.category_id=i.category_id
  where i.kind='live' and i.is_present and not i.is_adult and c.is_present
),
cleaned as (
  select b.*,
    btrim(
      regexp_replace(
        regexp_replace(
          regexp_replace(b.source_name,
            '^(AU|NZ|UK|US|TV|IR|NA|EN|PRIME|UK-NOWTV)([[:space:]]*\([^)]*\))?[[:space:]]*(\||:)[[:space:]]*',
            '', 'i'),
          '[[:space:]]+(UHD|4K|FHD|HD|HEVC|RAW|SD|VIP|H\.?265|60FPS|ᴿᴬᵂ|⁶⁰ᶠᵖˢ|◉)([[:space:]]+(UHD|4K|FHD|HD|HEVC|RAW|SD|VIP|H\.?265|60FPS|ᴿᴬᵂ|⁶⁰ᶠᵖˢ|◉))*[[:space:]]*$',
          '', 'i'),
        '[[:space:]]+\((S|ALT)\)[[:space:]]*$', '', 'i')
    ) display_name,
    case
      when b.source_name ~* '(4K|UHD|2160)' then 60
      when b.source_name ~* '(RAW.*60|60FPS|⁶⁰ᶠᵖˢ)' then 55
      when b.source_name ~* 'FHD|1080' then 50
      when b.source_name ~* 'HEVC|H\.?265' then 45
      when b.source_name ~* '(^|[[:space:]])HD([[:space:]]|$)|720' then 40
      when b.source_name ~* '(^|[[:space:]])SD([[:space:]]|$)' then 20
      else 30
    end name_quality_score
  from base b
  where not b.is_foreign_language and not b.is_obviously_broken
),
routed as (
  select c.*,
    case
      when c.source_name ~* '(YAHOO FINANCE|BLOOMBERG|CNBC|FOX BUSINESS|CHEDDAR (BUSINESS|NEWS))' then 'Entertainment'
      when c.category_id in ('384','2231') and c.source_name ~* '(SPORT|ESPN|RACING|RUGBY|AFL|NRL|CRICKET|GOLF|TENNIS)' then 'Australia'
      when c.route_policy='current_ppv' and c.source_name ~* '(SKY SPORTS BOX OFFICE|TNT BOX OFFICE|ITV BOX OFFICE)' then 'Sports'
      when c.category_id='2540' and c.source_name ~* '(ESPN|SPORT|NFL|NBA|MLB|NHL|GOLF|TENNIS|RACING|FORMULA|MOTOGP|(^|[^A-Z])F1([^A-Z]|$)|UFC|WWE|FIGHT)' then 'Sports'
      when c.category_id in ('2183','2186') and c.source_name ~* '(RUGBYPASS TV|CBS SPORTS GOLAZO|SPORTSGRID|STADIUM|TENNIS CHANNEL|NFL NETWORK|NBA TV|UFC|PREMIER SPORTS|TNT SPORTS)' then 'Sports'
      else c.route_top
    end proposed_top,
    case
      when c.category_id in ('384','2231') and c.source_name ~* '(SPORT|ESPN|RACING|RUGBY|AFL|NRL|CRICKET|GOLF|TENNIS)' then 'Sports'
      when c.source_name ~* '(SKY SPORTS BOX OFFICE|TNT BOX OFFICE|ITV BOX OFFICE)' then 'Combat Sports'
      when c.route_policy='current_ppv' and c.source_name ~* '(UFC|MMA)' then 'UFC / MMA'
      when c.route_policy='current_ppv' and c.source_name ~* '(BOX(ING)?|BARE[[:space:]]+KNUCKLE|BKFC)' then 'Boxing & Bare Knuckle'
      when c.route_policy='current_ppv' and c.source_name ~* 'WWE' then 'WWE'
      when c.route_policy='current_ppv' and c.source_name ~* 'NBA' then 'NBA'
      when c.route_policy='current_ppv' and c.source_name ~* '(RUGBY|NRL|NATIONS[[:space:]]+(CUP|CHAMPIONSHIP))' then 'Rugby'
      when c.route_policy='current_ppv' and c.source_name ~* '(^|[^A-Z])NZ([^A-Z]|$)|NEW ZEALAND' then 'NZ Live Events'
      when c.route_policy='current_ppv' and c.category_id='2219' then 'AU Live Events'
      when c.route_policy='current_ppv' and c.category_id='1568' then 'US Live Events'
      when c.route_policy='current_ppv' then 'UK Live Events'
      when c.source_name ~* '(YAHOO FINANCE|BLOOMBERG|CNBC|FOX BUSINESS|CHEDDAR (BUSINESS|NEWS))' then 'Business & Finance'
      when c.category_id in ('2183','2186') and c.source_name ~* '(RUGBYPASS TV|CBS SPORTS GOLAZO|SPORTSGRID|STADIUM|TENNIS CHANNEL|NFL NETWORK|NBA TV|UFC|PREMIER SPORTS|TNT SPORTS)' then 'Networks & Talk'
      when (c.route_top='Sports' or c.category_id in ('384','2231','2540','2183','2186')) and c.source_name ~* '(NFL NETWORK|NFL REDZONE)' then 'NFL'
      when (c.route_top='Sports' or c.category_id in ('384','2231','2540','2183','2186')) and c.source_name ~* '(NBA TV|WNBA)' then 'NBA & WNBA'
      when (c.route_top='Sports' or c.category_id in ('384','2231','2540','2183','2186')) and c.source_name ~* '(MLB NETWORK|THE MLB CHANNEL)' then 'MLB'
      when (c.route_top='Sports' or c.category_id in ('384','2231','2540','2183','2186')) and c.source_name ~* 'NHL NETWORK' then 'NHL'
      when (c.route_top='Sports' or c.category_id in ('384','2231','2540','2183','2186')) and c.source_name ~* '(RUGBY|RUGBYPASS)' then 'Rugby'
      when (c.route_top='Sports' or c.category_id in ('384','2231','2540','2183','2186')) and c.source_name ~* '(UFC|WWE|FIGHT NETWORK|BOXING)' then 'Combat Sports'
      when (c.route_top='Sports' or c.category_id in ('384','2231','2540','2183','2186')) and c.source_name ~* '(FORMULA|MOTOGP|MOTORSPORT|RALLY|F1)' then 'Motorsport'
      when (c.route_top='Sports' or c.category_id in ('384','2231','2540','2183','2186')) and c.source_name ~* '(GOLF|TENNIS CHANNEL)' and c.source_name !~* 'TENNIS CHANNEL PLUS' then 'Golf & Tennis'
      when c.category_id='2540' and c.source_name ~* '(ESPN|SPORT|NFL|NBA|MLB|NHL|GOLF|TENNIS|FORMULA|MOTOGP|UFC|WWE|FIGHT)' then 'Networks & Talk'
      when c.category_id='2292' and c.source_name ~* 'KIDS[[:space:]_-]*PREMIUM[[:space:]_-]*(0?[1-9]|1[0-2])([^0-9]|$)' then 'Movies'
      else c.route_sub
    end proposed_sub,
    case
      when c.route_policy='full' then true
      when c.route_policy='core_uk' and c.source_name ~* '(BBC ONE|BBC TWO|ITV[[:space:]]*1|CHANNEL 4|CHANNEL 5)' then true
      when c.route_policy='business_only' and c.source_name ~* '(YAHOO FINANCE|BLOOMBERG|CNBC|FOX BUSINESS|CHEDDAR (BUSINESS|NEWS))' then true
      when c.route_policy='high_value_only'
        and c.source_name ~* '(YAHOO FINANCE|BLOOMBERG|CNBC|FOX BUSINESS|CHEDDAR (BUSINESS|NEWS)|RUGBYPASS TV|CBS SPORTS GOLAZO|SPORTSGRID|STADIUM|TENNIS CHANNEL|NFL NETWORK|NBA TV|UFC|PREMIER SPORTS|TNT SPORTS)'
        and c.source_name !~* 'TENNIS CHANNEL PLUS' then true
      when c.route_policy='permanent_sports'
        and c.source_name ~* '(ESPN2|ESPNEWS|ESPNU|BIG TEN NETWORK|ACC NETWORK|SEC NETWORK|CBS SPORTS NETWORK|CBS SPORTS GOLAZO|(^|[^A-Z])FS1([^A-Z]|$)|(^|[^A-Z])FS2([^A-Z]|$)|FUBO SPORTS|STADIUM|SPORTSGRID|VSIN|FANDUEL TV|YAHOO SPORTS|GOLF CHANNEL|TENNIS CHANNEL|NFL NETWORK|NFL REDZONE|NBA TV|MLB NETWORK|NHL NETWORK|SKY SPORTS NEWS|SKY SPORTS|TNT SPORTS|EUROSPORT|PREMIER SPORTS|LFC TV|MUTV|RUGBYPASS TV|UFC|WWE|FIGHT NETWORK)'
        and c.source_name !~* 'TENNIS CHANNEL PLUS' then true
      when c.route_policy='motorsport_primary' and c.source_name ~* 'MOTOGP[[:space:]]+OB1' then true
      when c.route_policy='wwe_247_only' and c.source_name ~* 'WWE[[:space:]]+WRESTLING' then true
      when c.route_policy='current_ppv' and c.source_name ~* '(SKY SPORTS BOX OFFICE|TNT BOX OFFICE|ITV BOX OFFICE)' then true
      when c.route_policy='current_ppv'
        and c.event_date between p.as_of_date - 1 and p.through_date
        and c.source_name !~* '(NO EVENT|NO STREAM|OFFLINE|BLANK)'
        and (
          c.category_id='2219'
          or
          c.source_name ~* '(UFC|MMA|BOX(ING)?|BARE[[:space:]]+KNUCKLE|BKFC|WWE|NBA|RUGBY|NRL|AFL|NFL|MLB|NHL)'
          or (c.category_id='2219' and c.source_name ~* '(PREMIER LEAGUE MATCH|WSL|CRICKET|TENNIS)')
          or (c.category_id in ('1523','1528','614','2489') and c.source_name ~* '(PREMIER LEAGUE MATCH|WSL|FA CUP|EFL|CHAMPIONSHIP)')
        ) then true
      else false
    end is_visible_candidate
  from cleaned c
  cross join params p
),
canonicalised as (
  select r.*,
    case
      when r.category_id in ('615','765','2287') then
        '247 ' || btrim(regexp_replace(lower(regexp_replace(r.display_name,'^24/7[[:space:]]+','','i')),'[^a-z0-9]+',' ','g'))
      when r.category_id='2231' and r.display_name ~* '9NOW[[:space:]]+9GEM' then 'au 9gem'
      when r.category_id='2231' and r.display_name ~* '9NOW[[:space:]]+9GO' then 'au 9go'
      when r.category_id='2231' and r.display_name ~* '9NOW[[:space:]]+9LIFE' then 'au 9life'
      when r.category_id='2231' and r.display_name ~* '9NOW[[:space:]]+9RUSH' then 'au 9rush'
      when r.category_id='2231' and r.display_name ~* '9NOW[[:space:]]+CHANNEL[[:space:]]+9' then 'au nine'
      when r.category_id='384' and r.display_name ~* '^10[[:space:]]+PEACH' then 'au 10 peach'
      when r.category_id='384' and r.display_name ~* '^10[[:space:]]+SHAKE' then 'au 10 shake'
      when r.category_id='384' and r.display_name ~* '^(CHANNEL[[:space:]]+10|TEN)([[:space:]]|$)' then 'au ten'
      when r.category_id='384' and r.display_name ~* '^7FLIX' then 'au 7flix'
      when r.category_id='384' and r.display_name ~* '^CHANNEL[[:space:]]+7([[:space:]]|$)' then 'au seven'
      when r.category_id='384' and r.display_name ~* '^(CHANNEL[[:space:]]+)?7MATE' then 'au 7mate'
      when r.category_id='384' and r.display_name ~* '^(CHANNEL[[:space:]]+)?7TWO' then 'au 7two'
      when r.category_id='384' and r.display_name ~* '^ABC[[:space:]]+(BRISBANE|MELBOURNE|PERTH|SYDNEY)$' then 'au abc'
      when r.category_id='384' and r.display_name ~* '^ABC[[:space:]]+COMEDY' then 'au abc comedy'
      when r.category_id='384' and r.display_name ~* '^ABC[[:space:]]+KIDS' then 'au abc kids'
      when r.category_id='384' and r.display_name ~* '^ABC[[:space:]]+ME' then 'au abc me'
      when r.category_id='384' and r.display_name ~* '^CHANNEL[[:space:]]+9([[:space:]]|$)' then 'au nine'
      when r.category_id='384' and r.display_name ~* '^SBS[[:space:]]+FOOD' then 'au sbs food'
      when r.category_id='384' and r.display_name ~* '^SBS[[:space:]]+(BRISBANE|MELBOURNE|PERTH|SYDNEY)$' then 'au sbs'
      when r.category_id='384' and r.display_name ~* '^SBS[[:space:]]+VICELAND' then 'au sbs viceland'
      when r.category_id='384' and r.display_name ~* '^SBS[[:space:]]+WORLD[[:space:]]+MOVIES' then 'au sbs world movies'
      when r.category_id='384' and r.display_name ~* '^REDBULL[[:space:]]+TV' then 'au redbull tv'
      when r.category_id='384' and r.display_name ~* '^(KAYO[[:space:]]*-[[:space:]]*)?ESPN[[:space:]]+1' then 'au espn 1'
      when r.category_id='384' and r.display_name ~* '^(KAYO[[:space:]]*-[[:space:]]*)?ESPN[[:space:]]+2' then 'au espn 2'
      when r.category_id='384' and r.display_name ~* '^(KAYO[[:space:]]*-[[:space:]]*)?FOX[[:space:]]+SPORTS[[:space:]]+50[1-7]' then
        'au fox sports ' || substring(r.display_name from '50[1-7]')
      when r.category_id='384' and r.display_name ~* '^(KAYO[[:space:]]*-[[:space:]]*)?FOX[[:space:]]+SPORTS[[:space:]]+NEWS' then 'au fox sports news'
      when r.display_name ~* '^NFL NETWORK[[:space:]]*\(N[0-9]+\)' then 'nfl network'
      when r.display_name ~* '^NFL NETWORK([- ][A-Z]+)?$' then 'nfl network'
      when r.display_name ~* '^NBA TV([[:space:]]+HDTV)?$' then 'nba tv'
      when r.display_name ~* '^(ACC NETWORK|ACCN)' then 'acc network'
      when r.display_name ~* '^ESPN[[:space:]]*2$' then 'espn 2'
      when r.display_name ~* '^(ESPN[[:space:]]+NEWS|ESPNEWS)' then 'espnews'
      when r.display_name ~* '^(ESPN[[:space:]]*U|ESPNU)' then 'espnu'
      when r.display_name ~* '^CBS SPORTS (GALAZO|GOLAZO)' then 'cbs sports golazo network'
      when r.display_name ~* '^(THE )?MLB (CHANNEL|NETWORK)' then 'mlb network'
      else btrim(regexp_replace(lower(r.display_name),'[^a-z0-9]+',' ','g'))
    end canonical_name
  from routed r
),
policy_ranked as (
  select c.*,np.priority netflix_priority,
    case when c.proposed_sub='Netflix' then
      row_number() over (
        partition by (c.proposed_sub='Netflix')
        order by (np.priority is not null) desc,np.priority nulls last,
                 c.health_score desc,c.last_success_at desc nulls last,
                 c.measured_quality_score desc,c.name_quality_score desc,
                 c.last_seen_at desc,c.provider_item_id
      )
    end netflix_rank
  from canonicalised c
  left join netflix_priority np on np.title=c.canonical_name
),
visible_pool as (
  select *
  from policy_ranked
  where is_visible_candidate
    and proposed_top is not null and proposed_sub is not null
    and canonical_name <> ''
    and not is_dedicated_racing
    and not is_us_news
    and not is_us_affiliate
    and source_name !~* '(TENNIS CHANNEL PLUS|ESPN\+[[:space:]]*[0-9]+|EVENT ONLY)'
    and (proposed_sub <> 'Netflix' or netflix_rank <= 60)
),
ranked_visible as (
  select v.*,
    row_number() over (
      partition by canonical_name
      order by (proposed_sub='Netflix') desc,
               (proposed_sub='Business & Finance') desc,
               (proposed_top in ('New Zealand','Australia','Sports')) desc,
               health_score desc,last_success_at desc nulls last,
               measured_quality_score desc,name_quality_score desc,
               (epg_id is not null) desc,(artwork_url is not null) desc,
               last_seen_at desc,provider_item_id
    ) source_rank,
    count(*) over (partition by canonical_name) source_count
  from visible_pool v
),
proposed_best as (
  select
    'live:' || regexp_replace(canonical_name,'[[:space:]]+','-','g') canonical_key,
    case canonical_name
      when 'au 9gem' then '9Gem'
      when 'au 9go' then '9Go!'
      when 'au 9life' then '9Life'
      when 'au 9rush' then '9Rush'
      when 'au nine' then 'Nine'
      when 'au 10 peach' then '10 Peach'
      when 'au 10 shake' then '10 Shake'
      when 'au ten' then 'Ten'
      when 'au 7flix' then '7flix'
      when 'au seven' then 'Seven'
      when 'au 7mate' then '7mate'
      when 'au 7two' then '7two'
      when 'au abc' then 'ABC'
      when 'au abc comedy' then 'ABC Comedy'
      when 'au abc kids' then 'ABC Kids'
      when 'au abc me' then 'ABC ME'
      when 'au sbs' then 'SBS'
      when 'au sbs food' then 'SBS Food'
      when 'au sbs viceland' then 'SBS Viceland'
      when 'au sbs world movies' then 'SBS World Movies'
      when 'au redbull tv' then 'Red Bull TV'
      when 'au espn 1' then 'ESPN Australia'
      when 'au espn 2' then 'ESPN2 Australia'
      when 'au fox sports news' then 'Fox Sports News Australia'
      when 'cbs sports golazo network' then 'CBS Sports Golazo Network'
      when 'espnews' then 'ESPNEWS'
      else display_name
    end display_name,
    proposed_top top_category,
    proposed_sub subcategory,
    provider_item_id primary_provider_item_id,
    source_name primary_source_name,
    category_id primary_category_id,
    epg_id,artwork_url,
    health_score,measured_quality_score,name_quality_score,
    source_count,
    case
      when proposed_top='New Zealand' then 10
      when proposed_top='Australia' then 20
      when proposed_top='Sports' then 30
      when proposed_top='Entertainment' then 40
      when proposed_top='Movies' then 50
      when proposed_top='Kids & Family' then 60
      when proposed_top='Documentaries & Lifestyle' then 70
      when proposed_top='Streaming' then 80
      when proposed_top='24/7' then 90
      when proposed_top='PPV' then 100
      else 999
    end top_sort
  from ranked_visible
  where source_rank=1
),
proposed_customer as (
  select * from proposed_best
),
retained_backend as (
  select * from cleaned
),
review_queue as (
  select c.*,
    case
      when c.route_top is null and not c.category_is_event_pool and c.category_id not in ('622','1007','1960','2261')
        and c.source_name ~* '(YAHOO FINANCE|BLOOMBERG|CNBC|FOX BUSINESS|CHEDDAR (BUSINESS|NEWS)|RUGBYPASS|CBS SPORTS GOLAZO|SPORTSGRID|STADIUM|TENNIS CHANNEL|NFL NETWORK|NBA TV|MLB NETWORK|NHL NETWORK|SKY SPORTS|TNT SPORTS|EUROSPORT|PREMIER SPORTS|LFC TV|MUTV|UFC|WWE|FIGHT NETWORK)'
        then 'unmapped_high_value_candidate'
      when c.route_policy='current_ppv'
        and c.event_date between p.as_of_date - 1 and p.through_date
        and not coalesce(r.is_visible_candidate,false)
        and c.category_id <> '1523'
        and c.source_name !~* '(NO EVENT|NO STREAM|OFFLINE|BLANK)'
        then 'current_event_identity_review'
      when c.route_policy='current_ppv' and c.event_date is null
        and not c.is_generic_numbered_feed
        and not coalesce(r.is_visible_candidate,false)
        and c.source_name ~* '(UFC|MMA|BOX(ING)?|BARE[[:space:]]+KNUCKLE|BKFC|WWE|NBA|RUGBY|NRL|AFL|NFL|MLB|NHL)'
        and c.source_name !~* '(NO EVENT|NO STREAM|OFFLINE|BLANK)'
        and c.source_name !~* '^([^|]*\|[[:space:]]*)?UFC[[:space:]]*[0-9]+[[:space:]:|]*$'
        and c.source_name !~* 'UFC\|[[:space:]]*24/7'
        then 'undated_ppv_identity_review'
      when c.is_generic_numbered_feed and not c.category_is_event_pool
        and c.source_name !~* '(NO EVENT|NO STREAM|OFFLINE|BLANK|COACHELLA|TOMORROWLAND)'
        and c.source_name ~* '(UFC|MMA|PETTIS|BOX(ING)?|BARE[[:space:]]+KNUCKLE|BKFC|WWE|NBA|RUGBY|NRL)'
        then 'active_numbered_event_needs_date'
      else null
    end review_reason
  from cleaned c
  left join routed r on r.provider_item_id=c.provider_item_id
  cross join params p
),
removal_metrics as (
  select jsonb_build_object(
    'action_247_category_removed',(
      select count(distinct canonical_name) from canonicalised where category_id='2294'
    ),
    'netflix_reduction',(
      select count(distinct canonical_name) from canonicalised where proposed_sub='Netflix'
    )-(select count(*) from proposed_customer where subcategory='Netflix'),
    'best_nz_category_removed',(
      select count(distinct canonical_name) from canonicalised where category_id='1007'
    ),
    'external_nz_freeview_deferred',5,
    'movies_series_247_duplicate_sources_consolidated',(
      select count(*)-count(distinct canonical_name) from canonicalised where category_id in ('615','765')
    ),
    'australia_regional_duplicates_consolidated',(
      select count(*)-count(distinct canonical_name) from canonicalised
      where category_id in ('384','2231') and is_visible_candidate and not is_dedicated_racing
    ),
    'us_news_removed',(
      select count(distinct canonical_name) from canonicalised where is_visible_candidate and is_us_news
    ),
    'us_affiliate_duplicates_removed',(
      select count(distinct canonical_name) from canonicalised where is_visible_candidate and is_us_affiliate
    ),
    'sports_network_duplicate_sources_consolidated',(
      select count(*) from visible_pool where proposed_top='Sports' and proposed_sub='Networks & Talk'
    )-(
      select count(*) from proposed_customer where top_category='Sports' and subcategory='Networks & Talk'
    ),
    'racing_trackside_removed',(
      select count(distinct canonical_name) from canonicalised where is_visible_candidate and is_dedicated_racing
    ),
    'ppv_event_cleanup_from_v2',135-(select count(*) from proposed_customer where top_category='PPV'),
    'net_change_from_v2',2797-(select count(*) from proposed_customer)
  ) value
),
summary as (
  select jsonb_build_object(
    'raw_live_rows',(select count(*) from lounge_sync.best_inventory_items where kind='live'),
    'present_non_adult_rows',(select count(*) from lounge_sync.best_inventory_items where kind='live' and is_present and not is_adult),
    'backend_retained_sources',(select count(*) from retained_backend),
    'proposed_customer_visible_channels',(select count(*) from proposed_customer),
    'visible_candidate_rows_before_consolidation',(select count(*) from visible_pool),
    'duplicates_consolidated',(select count(*) from visible_pool)-(select count(*) from proposed_customer),
    'foreign_language_rows_excluded',(select count(*) from base where is_foreign_language),
    'broken_or_placeholder_rows_excluded',(select count(*) from base where is_obviously_broken),
    'event_feeds_backend_only',(
      select count(*) from cleaned c
      where c.category_is_event_pool
        and not exists(select 1 from proposed_customer p where p.primary_provider_item_id=c.provider_item_id)
    ),
    'manual_review_rows',(select count(*) from review_queue where review_reason is not null),
    'production_mutations',0
  ) value
),
top_category_counts as (
  select jsonb_agg(jsonb_build_object(
    'top_category',top_category,'channels',channels
  ) order by top_sort) value
  from (
    select top_category,min(top_sort) top_sort,count(*) channels
    from proposed_customer group by top_category
  ) x
),
category_counts as (
  select jsonb_agg(jsonb_build_object(
    'top_category',top_category,
    'subcategory',subcategory,
    'channels',channels
  ) order by top_sort,subcategory) value
  from (
    select top_category,subcategory,min(top_sort) top_sort,count(*) channels
    from proposed_customer
    group by top_category,subcategory
  ) x
),
review_counts as (
  select jsonb_agg(jsonb_build_object('reason',review_reason,'rows',rows) order by review_reason) value
  from (
    select review_reason,count(*) rows
    from review_queue
    where review_reason is not null
    group by review_reason
  ) x
),
review_details as (
  select jsonb_agg(jsonb_build_object(
    'provider_item_id',provider_item_id,
    'provider_category_id',category_id,
    'provider_category',provider_category,
    'source_name',source_name,
    'reason',review_reason
  ) order by review_reason,provider_category,source_name) value
  from review_queue where review_reason is not null
),
coverage as (
  select jsonb_agg(jsonb_build_object('pattern',pattern,'matches',matches) order by pattern) value
  from (
    select p.pattern,count(c.*) matches
    from (values
      ('ESPN'),('ESPN2'),('ESPNEWS'),('ESPNU'),('BIG TEN NETWORK'),('ACC NETWORK'),('SEC NETWORK'),
      ('CBS SPORTS NETWORK'),('CBS SPORTS GOLAZO'),('FS1'),('FS2'),('FUBO SPORTS'),('STADIUM'),
      ('SPORTSGRID'),('VSIN'),('FANDUEL TV'),('GOLF CHANNEL'),('TENNIS CHANNEL'),('NFL NETWORK'),
      ('NBA TV'),('MLB NETWORK'),('NHL NETWORK'),('SKY SPORTS NEWS'),('TNT SPORTS'),('EUROSPORT'),
      ('PREMIER SPORTS'),('LFC TV'),('MUTV'),('RUGBYPASS TV'),('UFC'),('WWE'),('FIGHT NETWORK'),
      ('YAHOO FINANCE'),('YAHOO SPORTS'),('BLOOMBERG'),('CNBC'),('FOX BUSINESS'),('CHEDDAR BUSINESS'),
      ('F1'),('MOTOGP'),('24/7 KIDS')
    ) p(pattern)
    left join proposed_customer c on c.display_name ~*
      case
        when p.pattern='FS1' then '(FS1|FOX SPORTS 1)'
        when p.pattern='FS2' then '(FS2|FOX SPORTS 2)'
        else p.pattern
      end
    group by p.pattern
  ) x
)
select 'summary' section,value from summary
union all select 'removals_by_v3_rule',value from removal_metrics
union all select 'top_category_counts',coalesce(value,'[]'::jsonb) from top_category_counts
union all select 'category_counts',coalesce(value,'[]'::jsonb) from category_counts
union all select 'review_counts',coalesce(value,'[]'::jsonb) from review_counts
union all select 'manual_review_rows',coalesce(value,'[]'::jsonb) from review_details
union all select 'high_value_coverage',coalesce(value,'[]'::jsonb) from coverage;