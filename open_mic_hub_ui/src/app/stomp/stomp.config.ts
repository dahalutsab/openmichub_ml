import { InjectableRxStompConfig } from '@stomp/ng2-stompjs';
import SockJS from 'sockjs-client/dist/sockjs';
import { environment } from '../environment/environment';

export const stompConfig: InjectableRxStompConfig = {
  webSocketFactory: () => {
    const token = localStorage.getItem('authToken') || '';
    console.log(`Connecting to WebSocket with token: ${token}`);
    return new SockJS(`${environment.wsUrl}?access_token=${encodeURIComponent(token)}`);
  },
  connectHeaders: {
    Authorization: `Bearer ${localStorage.getItem('authToken') || ''}`
  },
  heartbeatIncoming: 0,
  heartbeatOutgoing: 20000,
  reconnectDelay: 5000,
  debug: (msg: string): void => {
    console.log(new Date(), msg);
  }
};
