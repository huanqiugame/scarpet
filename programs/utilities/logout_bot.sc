// Created by huanqiugame (https://github.com/huanqiugame) on GitHub on Oct 7, 2025
// It logs out bots, preserving all actions, locations, and game modes, when all real players leave. It logs in bots, resuming their actions, locations, and game modes, when all real players rejoin.
// This is a modified version of the original keepalive app by Ghoulboy78.

__config() -> {
    'scope' -> 'global',
    'command_permission' -> 'all',
};

// the duration, in seconds, that fake players wait to log out if all real players left
global_FAKE_PLAYER_LOG_OUT_TIME_OUT = 300; 
// the required block states that must be present for the App to log out bots
global_REQUIRED_BLOCK_STATES = [ 
    // Format: 
    // [block_id, pos_x, pos_y, pos_z, block_state_id, block_state_value, dimension]
    // Note: namespace is NOT REQUIRED;
    //     'minecraft:stone' is not valid, but 'stone' is
    //     dimension's namespace is OPTIONAL;
    //
    // Example: check if redstone lamp in overworld at (11, 45, 14) is lit
    // ['minecraft:redstone_lamp', 11, 45, 14, 'lit', false, 'minecraft:overworld'],
];

global_translations = {
    'en_us' -> {
        'list_saved_players.pretext' -> 'Saved players: ',
        'list_commands.pretext' -> '\nThe game will run the following commands when the first player joins the game:\n',
        'list_commands.posttext' -> 'If the above list has any unwanted commands, use `/player <ID> stop` for corresponding bot (case sensitive).\n',
    },
    'zh_cn' -> {
        'list_saved_players.pretext' -> '已存储假人：',
        'list_commands.pretext' -> '\n游戏将会在所有真人玩家退出并重新加入时，运行以下命令：\n',
        'list_commands.posttext' -> '若有不希望重进时运行的命令，对相应假人使用/player <ID> stop即可（区分大小写）。\n',
    },
};

get_translation(translation_key) -> (
    language = 'en_us';
    if(has(global_translations, player()~'language'),
        language = player()~'language';
    );
    global_translations:language:translation_key;
);

spawn_players() -> (
    __spawn_players();
);

save_players() -> (
    __save_players();
);

run_player_commands() -> (
    commands = read_file('commands', 'json');
    for (commands,
        logger(_);
        run(_);
    );
);

check_required_block_states() -> (
    all_conditions_met = true;
    for (global_REQUIRED_BLOCK_STATES,
        in_dimension(_:6,
            this_block = block(
                _:1,
                _:2,
                _:3
            );
            if (
                this_block != _:0 ||
                block_state(this_block, _:4) != _:5
            ,
                all_conditions_met = false;
            );
        );
    );
    all_conditions_met;
);

__spawn_players() -> (
    data = read_file('data', 'json');
    print(data);
    if (data && data:'players',
        data = data:'players';
        for (data,
            spawn_command = str(
                'player %s spawn at %f %f %f facing %f %f in %s',
                _:'name',
                _:'x',
                _:'y',
                _:'z',
                _:'yaw',
                _:'pitch',
                _:'dim'
            );
            logger('info', spawn_command);
            run(spawn_command);
    //         modify(player(_:'name'), 'flying', _:'fly')
        );
    );
    schedule(30, 'run_player_commands');
);

__save_players() -> (
    data = {'players' -> []};
    saved = [];
    if (read_file('commands', 'json');,
        commands = read_file('commands', 'json');,
        // else
        commands = [];
    );
    for (filter(player('all'), _~'player_type' == 'fake'),
        pdata = {};
        pdata:'name' = _~'name';
        pdata:'dim' = _~'dimension';
        pdata:'x' = _~'x';
        pdata:'y' = _~'y';
        pdata:'z' = _~'z';
        pdata:'yaw' = _~'yaw';
        pdata:'pitch' = _~'pitch';
        pdata:'fly' = _~'flying';
        data:'players' += pdata;
        saved += _~'name';
        commands += str('gamemode', _~'gamemode', player);
    );
    write_file('data', 'json', data);
    if (saved, 
        logger('info', '[logout_bot App] Saved ' + saved + '.');
        print('Saved ' + saved + '.');
    );
);

// __on_server_starts() -> (
//   logger('[logout_bot App] Server Starts.');
//   task('__spawn_players');
// );

// __on_server_shuts_down() -> (
//   task('__save_players');
// );

__delete_bot_entries(action) -> (
    if (!action, action = '';);
    if (command~str('^player .* ', action),
        player = command~str('^player (.*) ', action);
        c_for(i = 0, i < length(commands), i+=1,
            if(commands:i~str(player, ' ', action),
                delete(commands, i);
                i+=-1;
            );
        );
        write_file('commands', 'json', commands);
    );
);

__on_player_command(player, command) -> (
    // Test if the command starts with /player, if so, read commands for later use
    if(command~'^player',
        if (read_file('commands', 'json');,
            commands = read_file('commands', 'json');,
            // else
            commands = [];
        );
    );
    
    // Save events that control the bot to do something continuously
    if (command~'^player .* continuous' || command~'^player .* interval' || command~'^player .* move' || command~'^player .* sneak' || command~'^player .* sprint' || command~'^player .* perTick' || command~'^player .* randomly',
        is_controlling_real_player = false;
        for (filter(player('all'), _~'player_type' != 'fake'),
            if (command~_,
                is_controlling_real_player = true;
            );
        );
        if (!is_controlling_real_player,
            commands += command;
            write_file('commands', 'json', commands);
        );
    );
    
    // When using '/player .. stop' or '/player .. kill', clear all stored events for the corresponding bot
    if (command~'^player .* stop',
        player = command~'^player (.*) stop';
        c_for(i = 0, i < length(commands), i+=1,
            if(commands:i~player,
                delete(commands, i);
                i+=-1;
            );
        );
        write_file('commands', 'json', commands);
    );
    if (command~'^player .* kill',
        player = command~'^player (.*) kill';
        c_for(i = 0, i < length(commands), i+=1,
            if(commands:i~player,
                delete(commands, i);
                i+=-1;
            );
        );
        write_file('commands', 'json', commands);
    );

    // '/player .. unsneak'; '/player .. unsprint' handling
    if (command~'^player .* unsneak',
        player = command~'^player (.*) unsneak';
        c_for(i = 0, i < length(commands), i+=1,
            if(commands:i~str(player, ' sneak'),
                delete(commands, i);
                i+=-1;
            );
        );
        write_file('commands', 'json', commands);
    );
    if (command~'^player .* unsprint',
        player = command~'^player (.*) unsprint';
        c_for(i = 0, i < length(commands), i+=1,
            if(commands:i~str(player, ' sprint'),
                delete(commands, i);
                i+=-1;
            );
        );
        write_file('commands', 'json', commands);
    );

    // Save player state when spawning or killing a bot
    if (command~'^player .* spawn' || command~'^player .* kill',
        schedule(20, '__save_players');
    );
    
    if(command~'^player',
        print(get_translation('list_commands.pretext'));
        print(' --------');
        print(join('\n', commands));
        print(' --------\n');
        print(get_translation('list_commands.posttext'))
    );
);

global_last_player_disconnect_at = 0;

__on_player_disconnects(player, reason) -> (
    if (player~'player_type' != 'fake',
        schedule(20, _() -> ( // wait one second to make sure player has properly logged out
            __save_players();
            if (length(filter(player('all'), _~'player_type' != 'fake')) == 0,
                logger('[logout_bot App] No real players exist. Fake players are scheduled to log out soon...');
                global_last_player_disconnect_at = time(); // record the time last player disconnects
                // wait for a certain amount of time to check
                schedule(global_FAKE_PLAYER_LOG_OUT_TIME_OUT * 20 - 20, 'recursive_check_and_log_out', global_last_player_disconnect_at, 0);
            );
        ));
    );
);

recursive_check_and_log_out(last_player_disconnect_at, number_of_attempts) -> (
    // if no real players exist, 
    // AND after waiting for a certain time, the time recorded is still the same (meaning no player logged in during this period),
    // check if required block states are still valid
    // 
    // if player exists, interrupt the recursive check
    // if required block states are not valid, continue the recursive check
    //
    // if all conditions are met, log out fake players
    if (length(filter(player('all'), _~'player_type' != 'fake')) == 0 && last_player_disconnect_at == global_last_player_disconnect_at,
        if (check_required_block_states(),
            // Now log out fake players
            logger('[logout_bot App] Considering no real players exist for ' + (time() - last_player_disconnect_at) / 1000 + ' seconds, log out bots.');
            for (filter(player('all'), _~'player_type' == 'fake'),
                run(str('/player %s kill', _~'name'))
            );
        , // else
            number_of_attempts += 1;
            schedule(20, 'recursive_check_and_log_out', last_player_disconnect_at, number_of_attempts);
            if (number_of_attempts % 120 == 0,
                logger('[logout_bot App] It has been ' + number_of_attempts + ' attempts to check if required block states are valid. Real players disconnects ' + (time() - last_player_disconnect_at) / 1000 + ' seconds ago.');
            );
        );
    );
);

__on_player_dies(player) -> (
    if (player~'player_type' == 'fake',
        schedule(20, '__save_players');
        if (read_file('commands', 'json');,
            commands = read_file('commands', 'json');
            c_for(i = 0, i < length(commands), i+=1,
                if(commands:i~player,
                    delete(commands, i);
                    i+=-1;
                );
            );
            logger(commands);
            write_file('commands', 'json', commands);
        );
    );
);

__on_player_connects(player) -> (
    if (player~'player_type' != 'fake',
        fake_players = filter(player('all'), _~'player_type' == 'fake');
        if (length(fake_players) == 0,
            __spawn_players();
        );
    );
);

__on_statistic(player, category, event, value) -> (
    // If the player is a real player and there are no fake players,
    // spawn fake players
    // print('__on_statistic is triggered.');
    if (player~'player_type' != 'fake' && length(filter(player('all'), _~'player_type' == 'fake')) == 0,
        schedule(20, '__spawn_players');
    );
);