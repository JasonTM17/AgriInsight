const rail = document.querySelector("#primary-rail");
const navToggle = document.querySelector("[data-nav-toggle]");
const filterDialog = document.querySelector("#filter-dialog");
const workspace = document.querySelector(".workspace");
const railCloseButton = document.querySelector(".rail-close");
const mobileNavigation = window.matchMedia("(max-width: 900px)");

document.querySelectorAll("svg:not([role='img'])").forEach((icon) => {
  icon.setAttribute("aria-hidden", "true");
  icon.setAttribute("focusable", "false");
});

function setRailOpen(isOpen, restoreFocus = false) {
  const openOnMobile = mobileNavigation.matches && isOpen;
  rail?.classList.toggle("is-open", openOnMobile);
  document.body.classList.toggle("nav-open", openOnMobile);
  navToggle?.setAttribute("aria-expanded", String(openOnMobile));
  if (navToggle) {
    navToggle.setAttribute("aria-label", openOnMobile ? "Đóng điều hướng" : "Mở điều hướng");
  }
  if (rail && mobileNavigation.matches && !openOnMobile) {
    rail.setAttribute("inert", "");
    rail.setAttribute("aria-hidden", "true");
  } else {
    rail?.removeAttribute("inert");
    rail?.removeAttribute("aria-hidden");
  }
  if (workspace && openOnMobile) {
    workspace.setAttribute("inert", "");
    workspace.setAttribute("aria-hidden", "true");
    railCloseButton?.focus();
  } else {
    workspace?.removeAttribute("inert");
    workspace?.removeAttribute("aria-hidden");
    if (restoreFocus) {
      navToggle?.focus();
    }
  }
}

navToggle?.addEventListener("click", () => {
  setRailOpen(navToggle.getAttribute("aria-expanded") !== "true");
});

document.querySelectorAll("[data-nav-close]").forEach((element) => {
  element.addEventListener("click", () => setRailOpen(false, true));
});

document.querySelectorAll(".rail-nav a").forEach((link) => {
  link.addEventListener("click", () => {
    setRailOpen(false, true);
  });
});

mobileNavigation.addEventListener("change", () => setRailOpen(false));
setRailOpen(false);

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && navToggle?.getAttribute("aria-expanded") === "true") {
    setRailOpen(false, true);
  }
});

document.querySelectorAll("[data-open-filters]").forEach((button) => {
  button.addEventListener("click", () => filterDialog?.showModal());
});

document.querySelectorAll(".decision-row").forEach((row) => {
  row.addEventListener("click", () => {
    const expanded = row.getAttribute("aria-expanded") === "true";
    const evidence = row.nextElementSibling;
    row.setAttribute("aria-expanded", String(!expanded));
    if (evidence?.classList.contains("decision-evidence")) {
      evidence.hidden = expanded;
    }
  });
});

filterDialog?.addEventListener("click", (event) => {
  if (event.target === filterDialog) {
    filterDialog.close("cancel");
  }
});
