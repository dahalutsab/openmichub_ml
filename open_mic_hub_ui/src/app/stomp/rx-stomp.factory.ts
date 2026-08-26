import { RxStomp } from '@stomp/rx-stomp';
import { InjectableRxStompConfig } from '@stomp/ng2-stompjs';

export function rxStompServiceFactory(config: InjectableRxStompConfig): RxStomp {
  const rxStomp = new RxStomp();
  rxStomp.configure(config);
  rxStomp.activate();
  return rxStomp;
}
