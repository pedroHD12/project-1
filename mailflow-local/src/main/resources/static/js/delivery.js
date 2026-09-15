"use strict";
const deliveryMode = document.querySelector("#mode");
if (deliveryMode) {
    const scheduleFields = document.querySelector("#schedule-fields");
    const repeatFields = document.querySelector("#repeat-fields");
    const dateField = document.querySelector("#scheduledAt");
    const updateSchedule = () => {
        const scheduled = deliveryMode.value !== "NOW";
        scheduleFields.hidden = !scheduled;
        dateField.required = scheduled;
        repeatFields.hidden = !["DAILY", "WEEKLY"].includes(deliveryMode.value);
    };
    deliveryMode.addEventListener("change", updateSchedule);
    updateSchedule();
}
