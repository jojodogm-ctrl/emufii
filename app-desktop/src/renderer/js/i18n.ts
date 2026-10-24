import { store } from './state.js';

export type LanguageCode = 'system' | 'en' | 'es' | 'fr';

const translations: Record<'en' | 'es' | 'fr', Record<string, string>> = {
  en: {
    // Navigation & Hub
    settings_title: 'Settings',
    settings_subtitle: 'Preferences, emulator backends, game folders and network mesh',
    back: 'Back',
    open: 'Open',
    save: 'Save',
    change: 'Change',
    clear: 'Clear',
    remove: 'Remove',
    restore: 'Restore',
    ready: 'Ready',
    missing: 'Missing',
    online: 'Online',
    offline: 'Offline',
    idle: 'Idle',
    checking: 'Checking...',

    // Hub Tile Titles & Subtitles
    general: 'General',
    general_sub: 'System language · Notifications, mesh network',
    library: 'Library',
    library_sub: 'ROM folders, art scrapers, hidden games',
    consoles: 'Consoles',
    consoles_sub: '{count} of {total} consoles',
    emulators: 'Emulators',
    emulators_sub: 'What to set up outside Emufii',
    appearance: 'Appearance',
    appearance_sub: '{theme} theme · {accent}',
    about: 'About',
    about_sub: 'Version 2.0.4 • Build 55',
    crash_logs: 'Crash logs',
    crash_logs_sub: 'What Emufii kept from its last unexpected exits',

    // General Page
    app_language: 'Application Language',
    app_language_sub: 'Display language for menus, controls, and guides',
    friend_alerts: 'Friend Online Alerts',
    friend_alerts_sub: 'Receive desktop notifications when friends join or host rooms',
    coordinator_url: 'Coordinator URL',
    coordinator_url_sub: 'Master server endpoint for room discovery and peer address exchange',
    server_health: 'Server Health Status',
    server_health_sub: 'Real-time connectivity to the global coordinator',
    virtual_interface: 'Virtual Network Interface',
    virtual_interface_sub: 'Local virtual adapter managed via NetworkManager / nmcli',
    tunnel_specs: 'Tunnel Specifications',
    tunnel_specs_sub: 'MTU 1420 • PersistentKeepalive 10s • DNS 10.67.0.1 • Subnet 10.66.0.0/16',

    // Library Page
    game_directories: 'Game Directories',
    no_directories: 'No folders configured yet. Add your ROM folder to populate your library.',
    add_folder: '+ Add Folder',
    rescan_library: 'Rescan Library',
    add_roms: 'Add ROM Games',
    add_roms_sub: 'Import game files (.iso, .rvz, .gba, .nds, etc.) directly into the selected game directory',
    add_roms_btn: 'Add ROMs',
    special_directory: 'SPECIAL VAULT',
    open_folder: 'Open Folder',
    open_special_folder: 'Open Special ROMs Vault',
    selected_target: 'TARGET',
    set_as_target: 'Set as Target Directory',
    copying_roms: 'Copying ROM games...',
    roms_added_success: '✓ Added {count} games to {folder}!',
    artwork_metadata: 'Artwork & Metadata',
    sgdb_key: 'SteamGridDB API Key',
    sgdb_key_sub: 'Custom fallback API key to fetch high-resolution covers and horizontal banners',
    hidden_games: 'Hidden Games',
    hidden_games_sub: '{count} games currently hidden from view',
    restore_hidden: 'Restore Hidden Games',
    hide_incompatible: 'Hide Incompatible Games',
    hide_incompatible_sub: 'Automatically hide titles verified as unplayable in online netplay',
    default_layout: 'Default Library Layout',
    default_layout_sub: 'Select your preferred presentation mode',
    default_sorting: 'Default Sorting',
    default_sorting_sub: 'Ordering preference when browsing titles',

    // Consoles Page
    supported_systems: 'Supported Systems',
    consoles_note: 'Toggle console visibility to customize which platforms and games appear in your library.',

    // Emulators Page
    backends_drivers: 'Emulator Backends & Drivers',
    download_all: 'Download All Missing',
    browse_binary: 'Change...',
    download_btn: 'Download',

    // Appearance Page
    theme_floor: 'Theme Floor',
    theme_floor_sub: 'Follow operating system or set a fixed color floor',
    accent_signature: 'Accent Signature',
    accent_signature_sub: 'Highlight color for selections, buttons, and badges',
    interface_sounds: 'Interface Sounds',
    interface_sounds_sub: 'Nintendo-inspired sound effects on hover, click, and toggles',
    fluid_animations: 'Fluid Animations',
    fluid_animations_sub: 'Smooth spring physics, tile zoom, and page transitions',

    // About Page
    about_card_title: 'EmuFii Desktop',
    about_card_body: 'Cross-platform peer-to-peer and online multiplayer frontend for standalone emulators. Play together with simple 6-character room codes over zero-config WireGuard mesh tunnels.',
    about_license: 'GNU General Public License v3.0 (AGPL-3.0) • Rounded M+ 1c Font by Morishita & SIL OFL 1.1',
    updates_section: 'Updates',
    auto_download_updates: 'Auto-Download Updates',
    auto_download_updates_sub: 'Automatically check for new EmuFii releases on launch and prompt to update',
    check_updates_title: 'Check for Updates',
    check_updates_sub: 'Verify if your EmuFii Desktop installation is up-to-date',
    check_updates_btn: 'Check Now',
    up_to_date_badge: 'UP TO DATE',
    update_available_badge: 'UPDATE AVAILABLE',
    community_support: 'Community & Support',
    discord_title: 'Discord Community',
    discord_sub: 'Join fellow players, find matches, and report issues',
    kofi_title: 'Support Development',
    kofi_sub: 'Help keep relay coordinators and infrastructure online',
    github_title: 'GitHub Repository',
    github_sub: 'View source code, build instructions, and issue tracker',

    // Crash Logs Page
    crash_logs_title: 'Crash Logs',
    crash_logs_empty: 'No crash logs recorded. Everything is running smoothly.',
    crash_logs_desc: 'Recorded diagnostics from unexpected quits or launcher errors.',
    report_issue: 'Report Issue on GitHub',
    clear_logs: 'Clear Crash Logs',

    // Profile (Floating Panel)
    share_last_game: 'Share Last Played Game',
    share_last_game_sub: 'Broadcast current game to friends on the coordinator',
    copy_code: 'Copy Code',
    change_photo: 'Change Photo',
    remove_photo: 'Remove',
    reset_profile: 'Reset Profile & Keys',
    reset_profile_confirm: 'Are you sure you want to reset your profile? This generates a fresh friend code and resets your WireGuard keys.',
  },

  es: {
    // Navigation & Hub
    settings_title: 'Ajustes',
    settings_subtitle: 'Preferencias, emuladores, carpetas de juegos y red WireGuard',
    back: 'Atrás',
    open: 'Abrir',
    save: 'Guardar',
    change: 'Cambiar',
    clear: 'Limpiar',
    remove: 'Eliminar',
    restore: 'Restaurar',
    ready: 'Listo',
    missing: 'Faltante',
    online: 'Conectado',
    offline: 'Desconectado',
    idle: 'Ausente',
    checking: 'Comprobando...',

    // Hub Tile Titles & Subtitles
    general: 'General',
    general_sub: 'Idioma del sistema · Notificaciones, red mesh',
    library: 'Biblioteca',
    library_sub: 'Carpetas de ROMs, carátulas, juegos ocultos',
    consoles: 'Consolas',
    consoles_sub: '{count} de {total} consolas',
    emulators: 'Emuladores',
    emulators_sub: 'Qué configurar fuera de EmuFii',
    appearance: 'Apariencia',
    appearance_sub: 'Tema {theme} · {accent}',
    about: 'Acerca de',
    about_sub: 'Versión 2.0.4 • Compilación 55',
    crash_logs: 'Registros de fallos',
    crash_logs_sub: 'Lo que EmuFii guardó de sus salidas inesperadas',

    // General Page
    app_language: 'Idioma de la Aplicación',
    app_language_sub: 'Idioma de visualización de menús, controles y guías',
    friend_alerts: 'Alertas de Amigos Conectados',
    friend_alerts_sub: 'Recibe notificaciones en el escritorio cuando tus amigos creen o entren a salas',
    coordinator_url: 'URL del Servidor Coordinador',
    coordinator_url_sub: 'Servidor maestro para descubrimiento de salas e intercambio de direcciones P2P',
    server_health: 'Estado del Servidor',
    server_health_sub: 'Conectividad en tiempo real con el coordinador global',
    virtual_interface: 'Interfaz de Red Virtual',
    virtual_interface_sub: 'Adaptador virtual local administrado vía NetworkManager / nmcli',
    tunnel_specs: 'Especificaciones del Túnel',
    tunnel_specs_sub: 'MTU 1420 • PersistentKeepalive 10s • DNS 10.67.0.1 • Subred 10.66.0.0/16',

    // Library Page
    game_directories: 'Carpetas de Juegos',
    no_directories: 'Aún no hay carpetas vinculadas. Añade tu carpeta de ROMs para poblar la biblioteca.',
    add_folder: '+ Añadir Carpeta',
    rescan_library: 'Reescanear Biblioteca',
    add_roms: 'Añadir Juegos ROM',
    add_roms_sub: 'Importa archivos de juegos (.iso, .rvz, .gba, .nds, etc.) directamente al directorio de juegos seleccionado',
    add_roms_btn: 'Añadir ROMs',
    special_directory: 'CARPETA ESPECIAL',
    open_folder: 'Abrir Carpeta',
    open_special_folder: 'Abrir Carpeta Especial de ROMs',
    selected_target: 'DESTINO',
    set_as_target: 'Seleccionar como Directorio Destino',
    copying_roms: 'Copiando juegos ROM...',
    roms_added_success: '✓ ¡Se añadieron {count} juegos a {folder}!',
    artwork_metadata: 'Carátulas y Metadatos',
    sgdb_key: 'Clave API de SteamGridDB',
    sgdb_key_sub: 'Clave opcional de respaldo para obtener carátulas y banners horizontales en alta definición',
    hidden_games: 'Juegos Ocultos',
    hidden_games_sub: '{count} juegos actualmente ocultos de la vista',
    restore_hidden: 'Restaurar Juegos Ocultos',
    hide_incompatible: 'Ocultar Juegos Incompatibles',
    hide_incompatible_sub: 'Ocultar automáticamente títulos verificados como injugables en netplay',
    default_layout: 'Diseño de Biblioteca por Defecto',
    default_layout_sub: 'Elige tu modo de presentación favorito',
    default_sorting: 'Ordenación por Defecto',
    default_sorting_sub: 'Preferencia de orden al explorar títulos',

    // Consoles Page
    supported_systems: 'Plataformas Soportadas',
    consoles_note: 'Activa o desactiva las consolas para personalizar qué sistemas y juegos aparecen en tu biblioteca.',

    // Emulators Page
    backends_drivers: 'Emuladores y Drivers de Red',
    download_all: 'Descargar Todo lo Faltante',
    browse_binary: 'Cambiar...',
    download_btn: 'Descargar',

    // Appearance Page
    theme_floor: 'Tema Base',
    theme_floor_sub: 'Adaptar al sistema operativo o fijar un tema permanente',
    accent_signature: 'Color de Acento',
    accent_signature_sub: 'Color característico para selecciones, botones y resaltados',
    interface_sounds: 'Sonidos de la Interfaz',
    interface_sounds_sub: 'Efectos de sonido estilo Nintendo al pasar el cursor y pulsar',
    fluid_animations: 'Animaciones Fluidas',
    fluid_animations_sub: 'Física elástica suave, zoom de carátulas y transiciones fluidas',

    // About Page
    about_card_title: 'EmuFii Desktop',
    about_card_body: 'Frontend multijugador P2P y online para emuladores independientes. Juega con amigos mediante códigos de sala de 6 caracteres sobre túneles seguros WireGuard.',
    about_license: 'Licencia GNU GPLv3 (AGPL-3.0) • Tipografía Rounded M+ 1c por Morishita & SIL OFL 1.1',
    updates_section: 'Actualizaciones',
    auto_download_updates: 'Descarga Automática de Actualizaciones',
    auto_download_updates_sub: 'Verificar nuevas versiones de EmuFii al iniciar y preguntar si deseas actualizar',
    check_updates_title: 'Buscar Actualizaciones',
    check_updates_sub: 'Comprueba si tu instalación de EmuFii Desktop está al día',
    check_updates_btn: 'Verificar',
    up_to_date_badge: 'AL DÍA',
    update_available_badge: 'ACTUALIZACIÓN DISPONIBLE',
    community_support: 'Comunidad y Soporte',
    discord_title: 'Comunidad de Discord',
    discord_sub: 'Únete a otros jugadores, busca partidas y reporta incidencias',
    kofi_title: 'Apoyar el Desarrollo',
    kofi_sub: 'Ayuda a mantener los servidores coordinadores y la infraestructura activa',
    github_title: 'Repositorio GitHub',
    github_sub: 'Ver código fuente, guías de compilación y control de versiones',

    // Crash Logs Page
    crash_logs_title: 'Registros de Fallos',
    crash_logs_empty: 'No hay registros de fallos. Todo funciona a la perfección.',
    crash_logs_desc: 'Diagnósticos almacenados de salidas inesperadas o errores de arranque.',
    report_issue: 'Reportar Problema en GitHub',
    clear_logs: 'Limpiar Registros',

    // Profile (Floating Panel)
    share_last_game: 'Compartir Último Juego Jugado',
    share_last_game_sub: 'Transmite el juego que estás ejecutando a tus amigos en el coordinador',
    copy_code: 'Copiar Código',
    change_photo: 'Cambiar Foto',
    remove_photo: 'Quitar',
    reset_profile: 'Resetear Perfil y Claves',
    reset_profile_confirm: '¿Estás seguro de que deseas resetear tu perfil? Se generará un nuevo Friend Code y se reiniciarán las claves WireGuard.',
  },

  fr: {
    // Navigation & Hub
    settings_title: 'Paramètres',
    settings_subtitle: 'Préférences, émulateurs, dossiers de jeux et réseau WireGuard',
    back: 'Retour',
    open: 'Ouvrir',
    save: 'Enregistrer',
    change: 'Modifier',
    clear: 'Effacer',
    remove: 'Supprimer',
    restore: 'Restaurer',
    ready: 'Prêt',
    missing: 'Manquant',
    online: 'En ligne',
    offline: 'Hors ligne',
    idle: 'Absent',
    checking: 'Vérification...',

    // Hub Tile Titles & Subtitles
    general: 'Général',
    general_sub: 'Langue du système · Notifications, réseau',
    library: 'Bibliothèque',
    library_sub: 'Dossiers de ROMs, jaquettes, jeux masqués',
    consoles: 'Consoles',
    consoles_sub: '{count} sur {total} consoles',
    emulators: 'Émulateurs',
    emulators_sub: 'À configurer en dehors d\'EmuFii',
    appearance: 'Apparence',
    appearance_sub: 'Thème {theme} · {accent}',
    about: 'À propos',
    about_sub: 'Version 2.0.4 • Build 55',
    crash_logs: 'Journaux de crash',
    crash_logs_sub: 'Ce qu\'EmuFii a conservé de ses dernières sorties inattendues',

    // General Page
    app_language: 'Langue de l\'Application',
    app_language_sub: 'Langue d\'affichage des menus, commandes et guides',
    friend_alerts: 'Alertes d\'Amis en Ligne',
    friend_alerts_sub: 'Recevez des notifications sur le bureau lorsque vos amis rejoignent des salons',
    coordinator_url: 'URL du Serveur Coordinateur',
    coordinator_url_sub: 'Serveur maître pour la découverte de salons et échange d\'adresses P2P',
    server_health: 'État du Serveur',
    server_health_sub: 'Connectivité en temps réel avec le coordinateur global',
    virtual_interface: 'Interface Réseau Virtuelle',
    virtual_interface_sub: 'Adaptateur virtuel local géré via NetworkManager / nmcli',
    tunnel_specs: 'Spécifications du Tunnel',
    tunnel_specs_sub: 'MTU 1420 • PersistentKeepalive 10s • DNS 10.67.0.1 • Sous-réseau 10.66.0.0/16',

    // Library Page
    game_directories: 'Dossiers de Jeux',
    no_directories: 'Aucun dossier configuré. Ajoutez votre dossier de ROMs pour remplir la bibliothèque.',
    add_folder: '+ Ajouter un dossier',
    rescan_library: 'Scanner à nouveau',
    add_roms: 'Ajouter des jeux ROM',
    add_roms_sub: 'Importez des fichiers de jeu (.iso, .rvz, .gba, .nds, etc.) directement dans le dossier sélectionné',
    add_roms_btn: 'Ajouter des ROMs',
    special_directory: 'DOSSIER SPÉCIAL',
    open_folder: 'Ouvrir le dossier',
    open_special_folder: 'Ouvrir le Dossier Spécial de ROMs',
    selected_target: 'CIBLE',
    set_as_target: 'Définir comme dossier cible',
    copying_roms: 'Copie des jeux ROM...',
    roms_added_success: '✓ {count} jeux ajoutés dans {folder} !',
    artwork_metadata: 'Jaquettes et Métadonnées',
    sgdb_key: 'Clé API SteamGridDB',
    sgdb_key_sub: 'Clé de secours facultative pour récupérer des jaquettes et bannières haute résolution',
    hidden_games: 'Jeux Masqués',
    hidden_games_sub: '{count} jeux actuellement masqués',
    restore_hidden: 'Restaurer les jeux masqués',
    hide_incompatible: 'Masquer les Jeux Incompatibles',
    hide_incompatible_sub: 'Masquer automatiquement les jeux signalés comme injouables en ligne',
    default_layout: 'Disposition par Défaut',
    default_layout_sub: 'Sélectionnez votre mode de présentation préféré',
    default_sorting: 'Tri par Défaut',
    default_sorting_sub: 'Ordre d\'affichage lors de l\'exploration des titres',

    // Consoles Page
    supported_systems: 'Systèmes Pris en Charge',
    consoles_note: 'Activez ou désactivez les consoles pour personnaliser les systèmes affichés dans votre bibliothèque.',

    // Emulators Page
    backends_drivers: 'Émulateurs et Pilotes Réseau',
    download_all: 'Tout Télécharger',
    browse_binary: 'Modifier...',
    download_btn: 'Télécharger',

    // Appearance Page
    theme_floor: 'Thème de Base',
    theme_floor_sub: 'Suivre le système d\'exploitation ou définir un thème fixe',
    accent_signature: 'Couleur d\'Accentuation',
    accent_signature_sub: 'Couleur pour les sélections, boutons et badges',
    interface_sounds: 'Effets Sonores',
    interface_sounds_sub: 'Effets audio inspirés de Nintendo au survol et au clic',
    fluid_animations: 'Animations Fluides',
    fluid_animations_sub: 'Physique des ressorts et transitions de page fluides',

    // About Page
    about_card_title: 'EmuFii Desktop',
    about_card_body: 'Frontend multijoueur P2P et en ligne pour émulateurs indépendants. Jouez ensemble grâce à des codes de salon à 6 caractères sur des tunnels WireGuard.',
    about_license: 'Licence GNU GPLv3 (AGPL-3.0) • Police Rounded M+ 1c par Morishita & SIL OFL 1.1',
    updates_section: 'Mises à jour',
    auto_download_updates: 'Téléchargement Automatique des Mises à Jour',
    auto_download_updates_sub: 'Vérifier automatiquement les nouvelles versions au lancement',
    check_updates_title: 'Vérifier les Mises à Jour',
    check_updates_sub: 'Vérifiez si votre installation d\'EmuFii Desktop est à jour',
    check_updates_btn: 'Vérifier',
    up_to_date_badge: 'À JOUR',
    update_available_badge: 'MISE À JOUR DISPONIBLE',
    community_support: 'Communauté et Assistance',
    discord_title: 'Communauté Discord',
    discord_sub: 'Rejoignez d\'autres joueurs, trouvez des parties et signalez des bugs',
    kofi_title: 'Soutenir le Projet',
    kofi_sub: 'Aidez à maintenir les serveurs coordinateurs et l\'infrastructure en ligne',
    github_title: 'Dépôt GitHub',
    github_sub: 'Voir le code source et les guides de compilation',

    // Crash Logs Page
    crash_logs_title: 'Journaux de Crash',
    crash_logs_empty: 'Aucun plantage enregistré. Tout fonctionne parfaitement.',
    crash_logs_desc: 'Diagnostics enregistrés lors de fermetures inattendues ou d\'erreurs.',
    report_issue: 'Signaler un Problème sur GitHub',
    clear_logs: 'Effacer les Journaux',

    // Profile (Floating Panel)
    share_last_game: 'Partager le Dernier Jeu Joué',
    share_last_game_sub: 'Diffusez votre jeu actuel à vos amis sur le coordinateur',
    copy_code: 'Copier le Code',
    change_photo: 'Changer la Photo',
    remove_photo: 'Supprimer',
    reset_profile: 'Réinitialiser le Profil',
    reset_profile_confirm: 'Êtes-vous sûr de vouloir réinitialiser votre profil ? Un nouveau code ami sera généré.',
  },
};

export function getActiveLanguage(): 'en' | 'es' | 'fr' {
  const pref = store.get().settings?.language || 'system';
  if (pref === 'es') return 'es';
  if (pref === 'fr') return 'fr';
  if (pref === 'en') return 'en';

  // System detection
  const nav = (typeof navigator !== 'undefined' ? navigator.language : 'en').toLowerCase();
  if (nav.startsWith('es')) return 'es';
  if (nav.startsWith('fr')) return 'fr';
  return 'en';
}

export function t(key: string, params?: Record<string, any>): string {
  const lang = getActiveLanguage();
  let str = translations[lang]?.[key] || translations.en[key] || key;

  if (params) {
    for (const [k, v] of Object.entries(params)) {
      str = str.replace(new RegExp(`\\{${k}\\}`, 'g'), String(v));
    }
  }

  return str;
}
