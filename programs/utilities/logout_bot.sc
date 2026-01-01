// Created by huanqiugame (https://github.com/huanqiugame) on GitHub on Oct 7, 2025
// It logs out bots, preserving all actions, locations, and game modes, when all real players leave. It logs in bots, resuming their actions, locations, and game modes, when all real players rejoin.
// This is a modified version of the original keepalive app by Ghoulboy78.

__config() -> {
    'scope' -> 'global',
    'command_permission' -> 'all',
};

global_FAKE_PLAYER_LOG_OUT_TIME_OUT = 300; // the duration, in seconds, that fake players wait to log out if all real players left

global_translations = {
    'en_us' -> {
        'list_commands.pretext' -> '\nThe game will run the following commands when the first player joins the game:\n',
        'list_commands.posttext' -> 'If the above list has any unwanted commands, use /player <ID> stop for corresponding bot (case sensitive).\n',
    },
    'zh_cn' -> {
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

__spawn_players() -> (
   data = load_app_data();
   if (data && data:'players',
      data = parse_nbt(data:'players');
      for (data,
         for([str('player %s spawn at %f %f %f facing %f %f in %s',
                  _:'name', _:'x', _:'y', _:'z', _:'yaw', _:'pitch', _:'dim')],
            logger('info', _);
            run(_);
         );
//         modify(player(_:'name'), 'flying', _:'fly')
      );
   );
   schedule(30, 'run_player_commands');
);

__save_players() -> (
   data = nbt('{players:[]}');
   saved = [];
   if (read_file('commands', 'json');,
      commands = read_file('commands', 'json');,
      // else
      commands = [];
   );
   for (filter(player('all'), _~'player_type' == 'fake'),
      pdata = nbt('{}');
      pdata:'name' = _~'name';
      pdata:'dim' = _~'dimension';
      pdata:'x' = _~'x';
      pdata:'y' = _~'y';
      pdata:'z' = _~'z';
      pdata:'yaw' = _~'yaw';
      pdata:'pitch' = _~'pitch';
      pdata:'fly' = _~'flying';
      put(data, 'players', pdata, -1);
      saved += _~'name';
      commands += str('gamemode', _~'gamemode', player)
   );
   store_app_data(data);
   if (saved, logger('info', '[logout_bot App] Saved '+saved+' for next startup'));
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
        // __save_players();
        schedule(20, _() -> ( // wait one tick to make sure player has properly logged out
            if (length(filter(player('all'), _~'player_type' != 'fake')) == 0,
                logger('[logout_bot App] No real players exist. Fake players are scheduled to log out soon...');
                global_last_player_disconnect_at = time(); // record the time last player disconnects
                // wait for a certain time to check
                schedule(global_FAKE_PLAYER_LOG_OUT_TIME_OUT * 20 - 20, _(last_player_disconnect_at) -> (
                    // if no real players exist AND after waiting for a certain time, the time recorded is still the same (meaning no player logged in during this period), log out fake players
                    if (length(filter(player('all'), _~'player_type' != 'fake')) == 0 && last_player_disconnect_at == global_last_player_disconnect_at,
                        logger('[logout_bot App] Considering no real players exist for ' + global_FAKE_PLAYER_LOG_OUT_TIME_OUT + ' seconds, log out bots.');
                        for (filter(player('all'), _~'player_type' == 'fake'),
                            run(str('/player %s kill', _~'name'))
                        );
                    );
                ), global_last_player_disconnect_at);
            );
        ));
    );
);

__on_player_dies(player) -> (
    logger('[logout_bot App] Running "player_dies"...');
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
    logger('[logout_bot App] Finish "player_dies"');
);

__on_player_connects(player) -> (
    if (player~'player_type' != 'fake',
        fake_players = filter(player('all'), _~'player_type' == 'fake');
        if (length(fake_players) == 0,
            __spawn_players();
        );
    );
);