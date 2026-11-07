declare const __LOUNGE_APP_ID__: string;
declare const __LOUNGE_APP_VERSION__: string;
declare const __LOUNGE_CUSTOMER__: boolean;
export const APP_ID =
  typeof __LOUNGE_APP_ID__ === "string"
    ? __LOUNGE_APP_ID__
    : "nz.loungetv.webos.qa";
export const APP_VERSION =
  typeof __LOUNGE_APP_VERSION__ === "string"
    ? __LOUNGE_APP_VERSION__
    : "0.1.12";
export const CUSTOMER_RELEASE =
  typeof __LOUNGE_CUSTOMER__ === "boolean" ? __LOUNGE_CUSTOMER__ : false;
export const SERVICE_URI = "luna://" + APP_ID + ".companion/";
