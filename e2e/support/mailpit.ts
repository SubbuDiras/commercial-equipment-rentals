import { expect } from '@playwright/test';
import { env } from './env';

interface MailpitMessage {
  ID: string;
  Subject: string;
  To: { Address: string }[];
}

/** Reads the local Mailpit inbox through its HTTP API (https://mailpit.axllent.org/docs/api-v1/). */
export const mailpit = {
  async clear(): Promise<void> {
    await fetch(`${env.mailpitUrl}/api/v1/messages`, { method: 'DELETE' });
  },

  /** Waits until an email to `to` whose subject contains `subject` arrives; returns its HTML body. */
  async waitFor(to: string, subject: string, timeoutMs = 20_000): Promise<{ subject: string; html: string }> {
    let found: MailpitMessage | undefined;
    await expect.poll(async () => {
      const query = encodeURIComponent(`to:"${to}" subject:"${subject}"`);
      const response = await fetch(`${env.mailpitUrl}/api/v1/search?query=${query}`);
      if (!response.ok) {
        return false;
      }
      const { messages } = await response.json() as { messages: MailpitMessage[] };
      found = messages.find(message => message.Subject.includes(subject)
        && message.To.some(recipient => recipient.Address.toLowerCase() === to.toLowerCase()));
      return !!found;
    }, { timeout: timeoutMs, message: `email to ${to} with subject containing "${subject}"` }).toBe(true);

    const message = await (await fetch(`${env.mailpitUrl}/api/v1/message/${found!.ID}`)).json() as { Subject: string; HTML: string };
    return { subject: message.Subject, html: message.HTML };
  }
};
