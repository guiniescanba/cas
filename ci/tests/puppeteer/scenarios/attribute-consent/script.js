const puppeteer = require('puppeteer');
const assert = require('assert');
const cas = require('../../cas.js');
const fs = require('fs');
const path = require("path");
const os = require("os");

(async () => {
    const browser = await puppeteer.launch(cas.browserOptions());
    const page = await cas.newPage(browser);

    await cas.goto(page, "https://localhost:8443/cas/login");
    await cas.loginWith(page);

    await cas.goto(page, "https://localhost:8443/cas/login?service=https://example.org");
    await cas.assertTextContent(page, '#content h2', "Attribute Consent");
    await cas.assertTextContent(page, "#appTitle", "The following attributes will be released to [https://example.org]:");
    await cas.assertTextContent(page, "#first-name", "first-name");
    await cas.assertTextContent(page, "#first-name-value", "[Apereo]");
    await cas.assertTextContent(page, "#last-name", "last-name");
    await cas.assertTextContent(page, "#last-name-value", "[CAS]");
    await cas.assertTextContent(page, "#email", "email");
    await cas.assertTextContent(page, "#email-value", "[casuser@example.org]");

    await cas.screenshot(page);
    await cas.click(page, "#optionsButton");
    await page.waitForTimeout(2000);

    await cas.screenshot(page);
    let opt = await page.$('#optionAlways');
    assert(opt != null);
    opt = await page.$('#optionAttributeName');
    assert(opt != null);
    opt = await page.$('#optionAttributeValue');
    assert(opt != null);
    await cas.assertTextContent(page, "#reminderTitle", "How often should I be reminded to consent again?");

    opt = await page.$('#reminder');
    assert(opt != null);
    opt = await page.$('#reminderTimeUnit');
    assert(opt != null);
    opt = await page.$('#cancel');
    assert(opt != null);

    let confirm = await page.$('#confirm');
    assert(confirm != null);
    await cas.click(page, "#confirm");
    await page.waitForNavigation();
    await cas.assertTicketParameter(page);
    
    const baseUrl = "https://localhost:8443/cas/actuator/attributeConsent";
    const url = `${baseUrl}/casuser`;
    console.log(`Trying ${url}`);
    let response = await cas.goto(page, url);
    console.log(`${response.status()} ${response.statusText()}`);
    assert(response.ok());
    
    let template = path.join(__dirname, 'consent-record.json');
    let body = fs.readFileSync(template, 'utf8');
    console.log(`Import consent record:\n${body}`);
    await cas.doRequest(`${baseUrl}/import`, "POST", {
        'Accept': 'application/json',
        'Content-Length': body.length,
        'Content-Type': 'application/json'
    }, 201, body);
    
    await cas.doGet(`${baseUrl}/export`,
    res => {
        const tempDir = os.tmpdir();
        let exported = path.join(tempDir, 'consent.zip');
        res.data.pipe(fs.createWriteStream(exported));
        console.log(`Exported consent records are at ${exported}`);
    },
    error => {
        throw error;
    }, {}, "stream");

    await cas.doRequest(`${baseUrl}/casuser/1`, "DELETE");
    await cas.doRequest(`${baseUrl}/casuser`, "DELETE");

    await browser.close();
})();


