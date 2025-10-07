__config() -> {
    'scope' -> 'global',
    'command_permission' -> 'all',
};

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
   logger('[logout_bot App] Running "spawn_players()"...');
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
   schedule(20, 'run_player_commands');
   logger('[logout_bot App] Finish "spawn_players()"');
);

__save_players() -> (
   logger('[logout_bot App] Running "save_players()"...');
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
   logger('[logout_bot App] Finish "save_players()"...');
);

// __on_server_starts() -> (
//   logger('[logout_bot App] Server Starts.');
//   task('__spawn_players');
// );

// __on_server_shuts_down() -> (
//   task('__save_players');
// );

get_commands() -> (
    print(read_file('commands', 'json'));
);

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
    // 测试命令是否以/player开头，如果是，读取commands以便后续使用
    if(command~'^player',
    logger('[logout_bot App] Player sends a /player command.');
        if (read_file('commands', 'json');,
            commands = read_file('commands', 'json');,
            // else
            commands = [];
        );
    );
    
    // 存储控制假人持续做某事的事件
    if (command~'^player .* continuous' || command~'^player .* interval' || command~'^player .* move' || command~'^player .* sneak' || command~'^player .* sprint',
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
    
    // ****** Debug ******
    // ****** Debug ******
    
    // 当使用/player .. stop或/player .. kill时，清除对应假人所有的已存储事件
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

    // /player .. unsneak; /player .. unsprint 处理
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

    // 保存玩家状态
    if (command~'^player .* spawn' || command~'^player .* kill',
        schedule(20, '__save_players');
    );
    
    if(command~'^player',
        print('\n游戏将会在所有真人玩家退出并重新加入时，运行以下命令：\n --------');
        print(join(',\n', commands));
        print(' --------');
        print('若有不希望重进时运行的命令，对相应玩家使用/player <ID> stop即可。\n')
    );
);

__kick_fake_players() -> (
    logger(filter(player('all'), _~'player_type' != 'fake'));
    if (length(filter(player('all'), _~'player_type' != 'fake')) == 0,
        for (filter(player('all'), _~'player_type' == 'fake'),
            run(str('/player %s kill', _~'name'))
        );
    );
);
__on_player_disconnects(player, reason) -> (
    logger('[logout_bot App] Running "player_disconnects"...');
    if (player~'player_type' != 'fake',
        // __save_players();
        schedule(10, '__kick_fake_players');
    );
    logger('[logout_bot App] Finish "player_disconnects"');
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
    logger('[logout_bot App] Running "player_connects"...');
    if (player~'player_type' != 'fake',
        fake_players = filter(player('all'), _~'player_type' == 'fake');
        if (length(fake_players) == 0,
            __spawn_players();
        );
    );
    logger('[logout_bot App] Finish "player_connects"...');
);