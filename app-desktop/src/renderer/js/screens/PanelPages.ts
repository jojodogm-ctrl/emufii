import { store } from '../state.js';
import { mountPanelPage, PanelPage } from '../components/FloatingPanel.js';
import { esc } from '../html.js';

const COPY: Record<PanelPage, { title: string; subtitle: string }> = {
  sessions: { title: 'Sessions', subtitle: 'Join a friend with their code, or hop into an open room.' },
  friends: { title: 'Friends', subtitle: 'See who is online and what they are playing.' },
};

export function renderPanelPage(container: HTMLElement, page: PanelPage) {
  const session = store.get().activeSession;
  container.innerHTML = `
    <div class="page panel-page">
      <header class="page-header">
        <div class="page-title-block">
          <h1 class="page-title">${COPY[page].title}</h1>
          <span class="page-subtitle">${COPY[page].subtitle}</span>
        </div>
        ${
          page === 'sessions' && session
            ? `<button class="btn-primary plate-button" id="btnResumeSession">Back to ${esc(session.code === 'ONLINE' ? 'your game' : session.code)}</button>`
            : ''
        }
      </header>
      <div class="page-scroll">
        <div class="page-body ${page}-body" id="panelPageBody"></div>
      </div>
    </div>
  `;
  container.querySelector('#btnResumeSession')?.addEventListener('click', () => store.navigate('session'));
  return mountPanelPage(container.querySelector<HTMLElement>('#panelPageBody')!, page);
}
