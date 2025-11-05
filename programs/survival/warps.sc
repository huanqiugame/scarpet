// Created by huanqiugame (https://github.com/huanqiugame) on GitHub on Oct 7, 2025
// This is a survival QoL app that allows you to set warp locations and teleport to them, and categorize your warp locations for better organization.
// # Usage
// - Adding a warp location: `/warps s <name> <category> <icon_item>`.
// - Deleting a warp locations: `/warps d <name> <category>`.
// - Viewing warp locations: `/warps`. This will open a big chest GUI, with the top row items representing the warp categories, and the rest representing the warp locations. Left clicking a warp location will teleport you to it.
// You can edit the name of this app to change the root command name for better access.
// e.g. Changing the app file to "wa.sc" will make all the command above starting with /wa.
// # Notes
// - This app is currently Chinese only.
// - This app can be used by Bedrock players if your Java server has a compatibility layer such as Geyser for Bedrock players to join. Try throwing away the warp-representative item as a substitute for left clicking.
// - This app has another version that stores warp locations separately on a player basis: homes.sc. This (each player has their own warp locations and categories) is the only difference, and its usage is the same except the leading command name.

translations -> {
    'English' -> {
        'startup' -> '启动',
        'set_warp' -> '设置路径点',
        'delete_warp' -> '删除路径点',
        'warp_set_success' -> '已保存路径点“%s”坐标 [%s, %s, %s]。',
        'warp_delete_success' -> '已删除路径点“%s”。',
        'warp_delete_fail' -> '未找到路径点“%}
    }
};

__config() -> {
    'strict' -> false,
    'scope' -> 'player',
    'command_permission' -> 'all',
    'commands' -> {
        '' -> 'startup',
        's <name>' -> ['set_warp', 'default', ['ender_pearl',1,'{count:1,id:"minecraft:ender_pearl"}']],
        's <name> <category>' -> ['set_warp', ['ender_pearl',1,'{count:1,id:"minecraft:ender_pearl"}']],
        's <name> <category> <item>' -> 'set_warp',
        'd <name>' -> ['delete_warp', 'default'],
        'd <name> <category>' -> 'delete_warp',
    },
    'arguments' -> {
        'name' -> {
            'type' -> 'string',
            'suggester' -> _(args) -> (
                sugg = ['""', '"---------- 输入路径点名称 ----------"', '"- 全英单词如MyHome可不带引号，否则都带 -"'];
                warps_data = read_file('warps', 'json');
                for (keys(warps_data),
                    for (keys(warps_data:_),
                        sugg += '"'+_+'"';
                    );
                );
                sugg;
            ),
        },
        'category' -> {
            'type' -> 'string',
            'suggester' -> _(args) -> (
                sugg = ['""', '"---------- 输入路径点类别 ----------"', '"- 全英单词如MyCate可不带引号，否则都带 -"', '"default"'];
                for (keys(read_file('warps', 'json')),
                    if (_ != 'default',
                        sugg += '"'+_+'"';
                    );
                );
                sugg;
            ),
        },
        'item' -> {
            'type' -> 'item',
        },
    },
};

get_warps_data() -> (
    if (read_file('warps', 'json');,
        warps_data = read_file('warps', 'json');,
        // else
        warps_data = {};
    );
    warps_data;
);

global_current_warp_page = 0;     // index starts at 0
global_current_category_page = 0; // index starts at 0
global_current_category = sort(keys(get_warps_data())):0;
global_warp_page_count = 1;
global_category_page_count = 1;
global_warps_item_list = [];      // dynamic, subject to category
global_current_warps_item_list = []; // dynamic, depends on global_warps_item_list and adjust to global_current_warp_page
global_categories_item_list = []; // stores names of all categories
global_current_categories_item_list = []; // similar to global_current_warps_item_list
global_current_page_warp_count = 0;
global_current_page_category_count = 0;
global_saved_screen_state = [];
global_update_items = ['warps', 'categories']; // available: 'warps' and 'categories'

set_warp(name, category, item) -> (
    if (test_for_player_move('为你添加路径点 ' + format('l ' + name), 1000) == false,
        // get player data
        warps_data = get_warps_data();
        warp_data = {};
        warp_dimension = player()~'dimension';
        warp_location = player()~'location';
        if (!item,
            item = ['ender_pearl',1,'{count:1,id:"minecraft:ender_pearl"}']
        );
        item:2 = decode_json(item:2);
        if (!item:2:'components',
            item:2:'components' = {'minecraft:custom_name' -> {'text' -> name, 'italic' -> false}};
            , // else
            item:2:'components':'minecraft:custom_name' = {'text' -> name, 'italic' -> false};
            if (item:2:'components':'minecraft:enchantment_glint_override' == '1b',
                item:2:'components':'minecraft:enchantment_glint_override' = 1;
            );
        );
        item:2 = encode_json(item:2);
        warp_data:'display_item' = item;
        warp_data:'location' = warp_location;
        warp_data:'dimension' = warp_dimension;
        if (!warps_data:category,
            warps_data:category = {};
        );
        warps_data:category:name = warp_data;
        
        // translate dimension name
        if (warp_dimension == 'overworld',
            warp_dimension_translation = '主世界';
        );
        if (warp_dimension == 'the_nether',
            warp_dimension_translation = '下界';
        );
        if (warp_dimension == 'the_end',
            warp_dimension_translation = '末地';
        );
        if (warp_dimension != 'overworld' && warp_dimension != 'the_nether' && warp_dimension != 'the_end',
            warp_dimension_translation = warp_dimension;
        );
        write_file('warps', 'json', warps_data);
        print(format('db [Warps] ', str('l 你已将“%s”坐标 [%s, %s, %s] 的位置保存为“%s”类别下的路径点“%s”。', warp_dimension_translation, warp_location:0, warp_location:1, warp_location:2, category, name)));
        logger('info', str('[Warps App] “%s”已将“%s”坐标 [%s, %s, %s] 的位置保存为“%s”类别下的路径点“%s”。', player()~'name', warp_dimension_translation, warp_location:0, warp_location:1, warp_location:2, category, name));
        display_title(player(), 'actionbar', format('l 已保存 ' + name + ' 。'), 0, 5, 20);
        global_update_items = ['warps', 'categories', 'display'];
    
        if (global_current_category == null,
            global_current_category = category;
        );
    );
);

delete_warp(name, category) -> (
    warps_data = get_warps_data();
    if (warps_data:category:name,
        delete(warps_data:category:name);
        print(format('db [Warps] ', str('l 你已删除“%s”类别下的路径点“%s”。', category, name)));
        logger('info', str('[Warps] “%s”已删除“%s”类别下的路径点“%s”。', player()~'name', category, name));
        if (warps_data:category == {},
            delete(warps_data:category);
            print(format('db [Warps] ', str('l 由于“%s”类别下已无路径点，已删除“%s”。', category, category)));
            logger('info', str('[Warps] 由于“%s”类别下已无路径点，已删除“%s”。', category, category));
            if (global_current_category == category,
                global_current_category = sort(keys(warps_data)):0;
            );
        );
        write_file('warps', 'json', warps_data);
        global_update_items = ['warps'];
        , // else
        print(format('db [Warps] ', str('r 没有找到“%s”类别下的路径点“%s”。', category, name)));
    );
    global_update_items = ['warps', 'categories', 'display'];
);

test_for_player_move(display_text, time_delay) -> (
    current_time = time();
    current_location = player()~'location';
    if (player()~'gamemode' == 'survival' || player()~'gamemode' == 'creative',
        player_moved = false;
        , // else
        player_moved = true;
        display_title(player(), 'actionbar', format('r 你并不处于生存模式！无法' + display_text + '。'), 0, 5, 20);
    );
    if (player_moved == false,
        while(time() - current_time <= time_delay,
            if (abs(current_location:0 - player()~'location':0) < 0.5 && abs(current_location:1 - player()~'location':1) < 1 && abs(current_location:2 - player()~'location':2) < 0.5,
                display_title(player(), 'actionbar', '将在 ' + format('l ' + (time_delay / 1000.0 - round((time() - current_time) / 100) / 10)) + ' 秒后' + display_text + '，请不要移动……', 0, 5, 20);
                game_tick(50);
                , // else
                player_moved = true;
                display_title(player(), 'actionbar', format('r 你移动了！取消' + display_text + '。'), 0, 5, 20);
                break();
            );
        );
    );
    player_moved;
);

startup() -> (
    warp_screen = create_screen(
        player(), 'generic_9x6', format('eb 传送点'), _(screen, player, action, data) -> (
            // update global_current_warp_page
            if (data:'slot' == 53 && global_current_warp_page + 1 < global_warp_page_count && (action == 'pickup' || action == 'quick_move' || action == 'swap' || action == 'throw' || action == 'pickup_all'),
                global_current_warp_page += 1;
                global_update_items = ['warps'];
                update_screen(screen);
            );
            if (data:'slot' == 52 && global_current_warp_page > 0 && (action == 'pickup' || action == 'quick_move' || action == 'swap' || action == 'throw' || action == 'pickup_all'),
                global_current_warp_page += -1;
                global_update_items = ['warps'];
                update_screen(screen);
            );

            // update global_current_category_page
            if (data:'slot' == 8 && global_current_category_page + 1 < global_category_page_count && (action == 'pickup' || action == 'quick_move' || action == 'swap' || action == 'throw' || action == 'pickup_all'),
                global_current_category_page += 1;
                global_update_items = ['categories'];
                update_screen(screen);
            );
            if (data:'slot' == 0 && global_current_category_page > 0 && (action == 'pickup' || action == 'quick_move' || action == 'swap' || action == 'throw' || action == 'pickup_all'),
                global_current_category_page += -1;
                global_update_items = ['categories'];
                update_screen(screen);
            );

            // update global_current_category
            if (inventory_get(screen, data:'slot'):2 && (action == 'pickup' || action == 'quick_move' || action == 'swap' || action == 'throw' || action == 'pickup_all') && [1,2,3,4,5,6,7]~(data:'slot') != null,
                global_current_category = inventory_get(screen, data:'slot'):2:'components':'minecraft:custom_name':'text';
                global_current_warp_page = 0;
                global_update_items = ['warps', 'categories'];
                update_screen(screen);
            );

            // teleport players
            warps_slot = [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43];
            if (inventory_get(screen, data:'slot'):2 && (action == 'pickup' || action == 'quick_move' || action == 'swap' || action == 'throw' || action == 'pickup_all') && warps_slot~(data:'slot') != null,
                warp_name = inventory_get(screen, data:'slot'):2:'components':'minecraft:custom_name':'text';
                data_location = get_warps_data():global_current_category:(inventory_get(screen, data:'slot'):2:'components':'minecraft:custom_name':'text');
                close_screen(screen);
                if (test_for_player_move('将你传送至 ' + format('l ' + warp_name), 1500) == false,
                    run(str('/execute in minecraft:%s run tp @s %s %s %s %s %s', data_location:'dimension', ...data_location:'location'));
                    display_title(player(), 'actionbar', format('l 你已传送至 ' + warp_name), 0, 5, 20);
                );
            );

            'cancel';
        )
    );

    if (global_update_items == [],
        global_update_items = ['display'];
    );
    update_screen(warp_screen);
);

update_screen(warp_screen) -> (

    if(get_warps_data() != {}, // Starts Big IF

    warps_data = get_warps_data();
    // global variables
    //// predefined
    warps_slot = [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43];
    categories_slot = [1, 2, 3, 4, 5, 6, 7];
    //// to be initialized


    // initialize global_categories_item_list
    // reuseable
    if (global_update_items~'categories' != null,
        global_categories_item_list = [];
        for (sort(keys(warps_data)),
            global_categories_item_list += _;
        );
    );

    // initialize global_warps_item_list
    // reuseable
    if (global_update_items~'warps' != null,
        global_warps_item_list = [];
        for (sort(keys(warps_data:global_current_category)),
            global_warps_item_list += warps_data:global_current_category:_:'display_item';
        );
    );

    // calculate global_warp_page_count and global_category_page_count
    // reuseable
    if (global_update_items~'warps' != null,
        global_warp_page_count = ceil(length(global_warps_item_list) / 28);
    );
    if (global_update_items~'categories' != null,
        global_category_page_count = ceil(length(global_categories_item_list) / 7);
    );

    // calculate global_current_page_warp_count and global_current_page_category_count
    // reuseable
    if (global_update_items~'warps' != null,
        if (length(global_warps_item_list) < 28,
            global_current_page_warp_count = length(global_warps_item_list);
            , // else
            if (global_current_warp_page + 1 == global_warp_page_count && length(global_warps_item_list) % 28 != 0,
                global_current_page_warp_count = length(global_warps_item_list) % 28;
                , // else
                global_current_page_warp_count = 28;
            );
        );
    );
    if (global_update_items~'categories' != null,
        if (length(global_categories_item_list) < 7,
            global_current_page_category_count = length(global_categories_item_list);
            , // else
            if (global_current_category_page + 1 == global_category_page_count && length(global_categories_item_list) % 7 != 0,
                global_current_page_category_count = length(global_categories_item_list) % 7;
                , // else
                global_current_page_category_count = 7;
            );
        );
    );

    // initialize global_current_categories_item_list
    // reuseable
    if (global_update_items~'categories' != null,
        if (length(global_categories_item_list) > 7,
            global_current_categories_item_list = slice(global_categories_item_list, global_current_category_page * 7, global_current_category_page * 7 + global_current_page_category_count);
            , // else
            global_current_categories_item_list = global_categories_item_list;
        );
    );

    // initialize global_current_warps_item_list
    if (global_update_items~'warps' != null,
        if (length(global_warps_item_list) > 28,
            global_current_warps_item_list = slice(global_warps_item_list, global_current_warp_page * 28, global_current_warp_page * 28 + global_current_page_warp_count);
            , // else
            global_current_warps_item_list = global_warps_item_list;
        );
    );

    // fill in UI controls
    // reuseable
    if (global_update_items~'warps' != null || global_update_items~'display' != null,
        if (global_warp_page_count > 1 && global_current_warp_page + 1 != global_warp_page_count,
            inventory_set(warp_screen, 53, 1, 'minecraft:arrow', '{components:{"minecraft:custom_name":{italic:false, text:"下一页"}},count:1,id:"minecraft:arrow"}');
            , // else
            inventory_set(warp_screen, 53, 1, 'minecraft:air');
        );
        if (global_current_warp_page > 0,
            inventory_set(warp_screen, 52, 1, 'minecraft:arrow', '{components:{"minecraft:custom_name":{italic:false, text:"上一页"}},count:1,id:"minecraft:arrow"}');
            , // else
            inventory_set(warp_screen, 52, 1, 'minecraft:air');
        );
    );
    if (global_update_items~'categories' != null || global_update_items~'display' != null,
        if (global_category_page_count > 1 && global_current_category_page + 1 != global_category_page_count,
            inventory_set(warp_screen, 8, 1, 'minecraft:arrow', '{components:{"minecraft:custom_name":{italic:false, text:"下一页"}},count:1,id:"minecraft:arrow"}');
            , // else
            inventory_set(warp_screen, 8, 1, 'minecraft:air');
        );
        if (global_current_category_page > 0,
            inventory_set(warp_screen, 0, 1, 'minecraft:arrow', '{components:{"minecraft:custom_name":{italic:false, text:"上一页"}},count:1,id:"minecraft:arrow"}');
            , // else
            inventory_set(warp_screen, 0, 1, 'minecraft:air');
        );
    );

    // fill in category tabs
    // reuseable
    if (global_update_items~'categories' != null || global_update_items~'display' != null,
        for (range(0, global_current_page_category_count),
            if (global_current_category == global_current_categories_item_list:_,
                inventory_set(warp_screen, categories_slot:_, 1, 'minecraft:fire_charge', str('{components:{"minecraft:custom_name":{italic:false, text:"%s"}},count:1,id:"minecraft:fire_charge"}', global_current_category));
                , // else
                inventory_set(warp_screen, categories_slot:_, 1, 'minecraft:snowball', str('{components:{"minecraft:custom_name":{italic:false, text:"%s"}},count:1,id:"minecraft:snowball"}', global_current_categories_item_list:_));
            );
        );
        for (range(global_current_page_category_count, 7),
            inventory_set(warp_screen, categories_slot:_, 1, 'minecraft:air');
        );
    );

    // fill in warps
    // reuseable
    if (global_update_items~'warps' != null || global_update_items~'display' != null,
        for (range(0, global_current_page_warp_count),
            inventory_set(warp_screen, warps_slot:_, global_current_warps_item_list:_:1, global_current_warps_item_list:_:0, global_current_warps_item_list:_:2);
        );
        for (range(global_current_page_warp_count, 28),
            inventory_set(warp_screen, warps_slot:_, 1, 'minecraft:air');
        );
    );

//    if (global_update_items != [],
//        global_saved_screen_state = [];
//        for (range(0, 54),
//            global_saved_screen_state += inventory_get(warp_screen, _);
//        );
//        , // else
//        for (range(0, 54),
//            if (global_saved_screen_state:_ == null,
//                inventory_set(warp_screen, _, 1, 'minecraft:air');
//                , // else
//                inventory_set(warp_screen, _, global_saved_screen_state:_:1, global_saved_screen_state:_:0, global_saved_screen_state:_:2);
//            );
//        );
//    );

    global_update_items = [];

    ); // Ends Big IF
);

