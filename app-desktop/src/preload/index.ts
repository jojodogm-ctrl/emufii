import { contextBridge, ipcRenderer } from 'electron';

export const api = {
  window: {
    minimize: () => ipcRenderer.invoke('window:minimize'),
    maximize: () => ipcRenderer.invoke('window:maximize'),
    close: () => ipcRenderer.invoke('window:close'),
    isMaximized: () => ipcRenderer.invoke('window:isMaximized'),
    onStateChanged: (callback: (state: { maximized: boolean }) => void) => {
      const listener = (_: any, data: any) => callback(data);
      ipcRenderer.on('window:state-changed', listener);
      return () => ipcRenderer.removeListener('window:state-changed', listener);
    },
  },
  config: {
    get: () => ipcRenderer.invoke('config:get'),
    updateProfile: (partial: any) => ipcRenderer.invoke('config:updateProfile', partial),
    updateSettings: (partial: any) => ipcRenderer.invoke('config:updateSettings', partial),
    addFriend: (code: string, nickname?: string) => ipcRenderer.invoke('config:addFriend', code, nickname),
    removeFriend: (code: string) => ipcRenderer.invoke('config:removeFriend', code),
    noteFriendMetadata: (
      names: Record<string, string>,
      avatars?: Record<string, string>,
      hashes?: Record<string, string>
    ) => ipcRenderer.invoke('config:noteFriendMetadata', names, avatars, hashes),
    renameRom: (id: string, title: string) => ipcRenderer.invoke('config:renameRom', id, title),
    toggleHideRom: (id: string) => ipcRenderer.invoke('config:toggleHideRom', id),
    restoreHiddenRoms: () => ipcRenderer.invoke('config:restoreHiddenRoms'),
    resetProfile: () => ipcRenderer.invoke('config:resetProfile'),
    setConsoleVisible: (consoleName: string, visible: boolean) =>
      ipcRenderer.invoke('config:setConsoleVisible', consoleName, visible),
  },
  dialog: {
    selectDirectory: () => ipcRenderer.invoke('dialog:selectDirectory'),
    selectRomFile: () => ipcRenderer.invoke('dialog:selectRomFile'),
    selectRomFiles: () => ipcRenderer.invoke('dialog:selectRomFiles'),
    selectExecutable: (name: string) => ipcRenderer.invoke('dialog:selectExecutable', name),
    selectAvatar: () => ipcRenderer.invoke('dialog:selectAvatar'),
  },
  library: {
    scan: () => ipcRenderer.invoke('library:scan'),
    addFile: (filePath: string) => ipcRenderer.invoke('library:addFile', filePath),
    removeFile: (filePath: string) => ipcRenderer.invoke('library:removeFile', filePath),
    addDirectory: (dir: string) => ipcRenderer.invoke('library:addDirectory', dir),
    removeDirectory: (dir: string) => ipcRenderer.invoke('library:removeDirectory', dir),
    getSpecialRomDirectory: () => ipcRenderer.invoke('library:getSpecialRomDirectory'),
    openPath: (targetPath?: string) => ipcRenderer.invoke('library:openPath', targetPath),
    importRomFiles: (filePaths: string[], targetDir?: string) =>
      ipcRenderer.invoke('library:importRomFiles', filePaths, targetDir),
    fetchCompat: () => ipcRenderer.invoke('library:fetchCompat'),
    fetchMeta: () => ipcRenderer.invoke('library:fetchMeta'),
  },
  coordinator: {
    checkHealth: () => ipcRenderer.invoke('coordinator:checkHealth'),
    createSession: (params: any) => ipcRenderer.invoke('coordinator:createSession', params),
    patchSession: (code: string, ip: string, port: number, token?: string) =>
      ipcRenderer.invoke('coordinator:patchSession', code, ip, port, token),
    setHostReady: (code: string, ready: boolean, token?: string) =>
      ipcRenderer.invoke('coordinator:setHostReady', code, ready, token),
    getSession: (code: string) => ipcRenderer.invoke('coordinator:getSession', code),
    listRelays: () => ipcRenderer.invoke('coordinator:listRelays'),
    listSessions: () => ipcRenderer.invoke('coordinator:listSessions'),
    heartbeat: (code: string, id: string, name: string, rttMs?: number) =>
      ipcRenderer.invoke('coordinator:heartbeat', code, id, name, rttMs),
    leaveSession: (code: string, id: string, token?: string) =>
      ipcRenderer.invoke('coordinator:leaveSession', code, id, token),
    deleteSession: (code: string, token?: string) =>
      ipcRenderer.invoke('coordinator:deleteSession', code, token),
    announcePresence: (id: string, name: string, inSession?: boolean) =>
      ipcRenderer.invoke('coordinator:announcePresence', id, name, inSession),
    announceLastGame: (id: string, title?: string | null, titleId?: string | null) =>
      ipcRenderer.invoke('coordinator:announceLastGame', id, title, titleId),
    friendStatuses: (codes: string[]) => ipcRenderer.invoke('coordinator:friendStatuses', codes),
    claimAddress: (code: string, pubKey: string, name?: string, profileId?: string) =>
      ipcRenderer.invoke('coordinator:claimAddress', code, pubKey, name, profileId),
    uploadAvatar: (id: string, key: string, imageBase64: string) =>
      ipcRenderer.invoke('coordinator:uploadAvatar', id, key, imageBase64),
    deleteAvatar: (id: string, key: string) =>
      ipcRenderer.invoke('coordinator:deleteAvatar', id, key),
    fetchAvatar: (id: string) => ipcRenderer.invoke('coordinator:fetchAvatar', id),
    fetchMemberAvatar: (code: string, handle: string) =>
      ipcRenderer.invoke('coordinator:fetchMemberAvatar', code, handle),
    sendFriendRequest: (targetCode: string, fromCode: string, fromName: string) =>
      ipcRenderer.invoke('coordinator:sendFriendRequest', targetCode, fromCode, fromName),
    fetchFriendInbox: (myCode: string) =>
      ipcRenderer.invoke('coordinator:fetchFriendInbox', myCode),
    answerFriendRequest: (myCode: string, fromCode: string, accept: boolean, myName?: string) =>
      ipcRenderer.invoke('coordinator:answerFriendRequest', myCode, fromCode, accept, myName),
    sendSessionInvite: (fromCode: string, toCode: string, sessionCode: string, fromName: string) =>
      ipcRenderer.invoke('coordinator:sendSessionInvite', fromCode, toCode, sessionCode, fromName),
    dismissSessionInvite: (myCode: string, fromCode: string, sessionCode: string) =>
      ipcRenderer.invoke('coordinator:dismissSessionInvite', myCode, fromCode, sessionCode),
    clearFriendRequestInbox: (myCode: string) =>
      ipcRenderer.invoke('coordinator:clearFriendRequestInbox', myCode),
    pingHost: (host: string) =>
      ipcRenderer.invoke('coordinator:pingHost', host),
    measureRelays: () =>
      ipcRenderer.invoke('coordinator:measureRelays'),
    reportJoinFailure: (reason: string, consoleType?: string | null, code?: string | null) =>
      ipcRenderer.invoke('coordinator:reportJoinFailure', reason, consoleType, code),
    reportEvent: (params: any) =>
      ipcRenderer.invoke('coordinator:reportEvent', params),
  },
  crashLogs: {
    list: () => ipcRenderer.invoke('crashLogs:list'),
    clear: () => ipcRenderer.invoke('crashLogs:clear'),
    openFolder: () => ipcRenderer.invoke('crashLogs:openFolder'),
  },
  wireguard: {
    up: (info: any, dns?: string | null) => ipcRenderer.invoke('wireguard:up', info, dns),
    down: () => ipcRenderer.invoke('wireguard:down'),
    status: () => ipcRenderer.invoke('wireguard:status'),
  },
  emulators: {
    detectAll: () => ipcRenderer.invoke('emulators:detectAll'),
    download: (consoleType: string) => ipcRenderer.invoke('emulators:download', consoleType),
    downloadAll: () => ipcRenderer.invoke('emulators:downloadAll'),
    isDownloaded: (consoleType: string) => ipcRenderer.invoke('emulators:isDownloaded', consoleType),
    onDownloadProgress: (callback: (data: { consoleType: string; percent: number; status: string }) => void) => {
      const listener = (_: any, data: any) => callback(data);
      ipcRenderer.on('emulators:download-progress', listener);
      return () => ipcRenderer.removeListener('emulators:download-progress', listener);
    },
    onGameClosed: (callback: () => void) => {
      const listener = () => callback();
      ipcRenderer.on('emulators:game-closed', listener);
      return () => ipcRenderer.removeListener('emulators:game-closed', listener);
    },
    killActiveGame: () => ipcRenderer.invoke('emulators:killActiveGame'),
    launchGame: (consoleType: string, options: any) =>
      ipcRenderer.invoke('emulators:launchGame', consoleType, options),
  },
  updates: {
    check: () => ipcRenderer.invoke('app:checkForUpdates'),
    perform: (info: any) => ipcRenderer.invoke('app:performUpdate', info),
    onProgress: (callback: (data: { percent: number; status: string }) => void) => {
      const listener = (_: any, data: any) => callback(data);
      ipcRenderer.on('app:update-progress', listener);
      return () => ipcRenderer.removeListener('app:update-progress', listener);
    },
  },
  installer: {
    launchInstalledApp: () => ipcRenderer.invoke('installer:launchInstalledApp'),
  },
  openExternal: (url: string) => ipcRenderer.invoke('app:openExternal', url),
};

contextBridge.exposeInMainWorld('emufii', api);
