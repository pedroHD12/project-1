"use strict";

const errorSummary = document.querySelector("[role='alert']");
if (errorSummary) {
    errorSummary.setAttribute("tabindex", "-1");
    errorSummary.focus();
}

const menu = document.querySelector(".navigation-disclosure");
if (menu) {
    const desktop = window.matchMedia("(min-width: 901px)");
    const updateMenu = () => { menu.open = desktop.matches; };
    updateMenu();
    desktop.addEventListener("change", updateMenu);
    menu.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !desktop.matches) {
            menu.open = false;
            menu.querySelector("summary").focus();
        }
    });
}

const customEmail = document.querySelector("#custom-email");
const provider = document.querySelector("#provider");
if (customEmail && provider) {
    const options = document.querySelector("#provider-options");
    const settings = document.querySelector("#custom-settings");
    const updateProvider = () => {
        options.hidden = !customEmail.checked;
        provider.disabled = !customEmail.checked;
        const manual = customEmail.checked && provider.value === "CUSTOM";
        settings.hidden = !manual;
        settings.querySelectorAll("input, select").forEach((field) => { field.disabled = !manual; });
    };
    customEmail.addEventListener("change", () => {
        if (customEmail.checked && provider.value === "AUTO") provider.value = "GOOGLE";
        updateProvider();
    });
    provider.addEventListener("change", updateProvider);
    updateProvider();
}

document.querySelectorAll("form[data-confirm]").forEach((form) => {
    form.addEventListener("submit", (event) => {
        if (!window.confirm(form.dataset.confirm)) event.preventDefault();
    });
});
document.querySelectorAll("form[method='post']").forEach((form) => {
    form.addEventListener("submit", (event) => {
        if (event.defaultPrevented) return;
        form.querySelectorAll("button[type='submit']").forEach((button) => {
            button.disabled = true;
            button.dataset.label = button.textContent;
            button.textContent = "Aguarde…";
        });
    });
});
window.addEventListener("pageshow", () => {
    document.querySelectorAll("button[data-label]").forEach((button) => {
        button.disabled = false;
        button.textContent = button.dataset.label;
    });
});
