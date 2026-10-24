import type { RomItem } from '../../main/library/scanner.js';
import type { RemoteSession, WgTunnelInfo, FriendsReply } from '../../main/network/coordinator.js';
import type { FriendRequestItem, SessionInviteEntry } from '../../main/library/storage.js';

export type ScreenType = 'splash' | 'library' | 'join' | 'session' | 'friends' | 'finder' | 'settings';

export interface InPanelFriendNotice {
  type: 'request' | 'accepted' | 'invite' | 'online' | 'playing';
  name: string;
  subtitle: string;
  avatarUrl?: string | null;
  code?: string;
  sessionCode?: string;
  at: number;
}

export interface AppState {
  currentScreen: ScreenType;
  screenHistory: ScreenType[];

  // Profile & Config
  profile: {
    id: string;
    name: string;
    avatarDataUrl?: string | null;
    ownerKey?: string;
  };
  settings: {
    romDirectories: string[];
    romFiles?: string[];
    theme: 'system' | 'light' | 'dark' | 'oled' | 'day' | 'night';
    accent: 'teal' | 'coral' | 'cyan' | 'purple' | 'green' | 'yellow' | 'red' | 'blue';
    animations: boolean;
    soundEffects: boolean;
    libraryLayout: 'grid' | 'carousel' | 'list';
    librarySort: 'az' | 'date' | 'console';
    coordinatorUrl: string;
    customEmulatorPaths: Record<string, string>;
    hiddenRoms: string[];
    customTitles: Record<string, string>;
    friends: string[];
    friendNicknames: Record<string, string>;
    friendNames?: Record<string, string>;
    friendAvatars?: Record<string, string>;
    friendAvatarHashes?: Record<string, string>;
    pendingFriendRequests?: FriendRequestItem[];
    pendingSessionInvites?: SessionInviteEntry[];
    steamGridDbKey?: string;
    language: 'system' | 'en' | 'es' | 'fr';
    hiddenConsoles: string[];
    hideIncompatible: boolean;
    notifyFriends: boolean;
    shareLastGame: boolean;
    autoDownloadUpdates: boolean;
  };

  // Real-time Friend Statuses
  friendStatuses: FriendsReply;

  // In-panel Friend Notification Banner (shown inside Friends floating panel when open)
  inPanelFriendNotice?: InPanelFriendNotice | null;

  // Library
  roms: RomItem[];
  consoleFilter: string; // 'ALL' or ConsoleType
  searchQuery: string;
  compatDb: Record<string, any>;
  metaDb: Record<string, any>;

  // Session
  activeSession: {
    code: string;
    isHost: boolean;
    rom?: RomItem | null;
    token?: string | null;
    remote?: RemoteSession | null;
    wireguard?: WgTunnelInfo | null;
    wireguardConfigPath?: string | null;
    wireguardNative?: boolean;
    step1Done: boolean;
    step2Done: boolean;
    netplayBusy: boolean;
    launching?: boolean;
  } | null;

  // Selected ROM for dialog
  selectedRom: RomItem | null;
}

type Listener = (state: AppState) => void;

class StateStore {
  private state: AppState = {
    currentScreen: 'splash',
    screenHistory: [],
    profile: { id: '', name: 'Joueur', avatarDataUrl: null },
    settings: {
      romDirectories: [],
      romFiles: [],
      theme: 'dark',
      accent: 'teal',
      animations: true,
      soundEffects: true,
      libraryLayout: 'grid',
      librarySort: 'console',
      coordinatorUrl: 'https://coord.emufii.xyz',
      customEmulatorPaths: {},
      hiddenRoms: [],
      customTitles: {},
      friends: [],
      friendNicknames: {},
      friendNames: {},
      friendAvatars: {},
      friendAvatarHashes: {},
      pendingFriendRequests: [],
      pendingSessionInvites: [],
      steamGridDbKey: '',
      language: 'system',
      hiddenConsoles: [],
      hideIncompatible: false,
      notifyFriends: true,
      shareLastGame: true,
      autoDownloadUpdates: true,
    },
    friendStatuses: { present: {}, names: {}, avatars: {}, lastGames: {} },
    inPanelFriendNotice: null,
    roms: [],
    consoleFilter: 'ALL',
    searchQuery: '',
    compatDb: {},
    metaDb: {},
    activeSession: null,
    selectedRom: null,
  };

  private listeners: Set<Listener> = new Set();

  get(): AppState {
    return this.state;
  }

  set(partial: Partial<AppState>) {
    this.state = { ...this.state, ...partial };
    this.notify();
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify() {
    for (const listener of this.listeners) {
      listener(this.state);
    }
  }

  navigate(screen: ScreenType) {
    if (this.state.currentScreen === screen) return;
    const history = [...this.state.screenHistory, this.state.currentScreen];
    this.set({ currentScreen: screen, screenHistory: history });
  }

  goBack(): boolean {
    if (this.state.screenHistory.length === 0) return false;
    const history = [...this.state.screenHistory];
    const prev = history.pop()!;
    this.set({ currentScreen: prev, screenHistory: history });
    return true;
  }
}

export const store = new StateStore();
