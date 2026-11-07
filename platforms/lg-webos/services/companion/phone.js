(function () {
  var token = location.hash.slice(1),
    form = document.getElementById("phone-form"),
    message = document.getElementById("message"),
    submit = document.getElementById("submit");
  history.replaceState(null, "", location.pathname);
  if (!/^[a-f0-9]{64}$/.test(token)) {
    message.textContent = "Scan the QR code currently shown on your TV.";
    submit.disabled = true;
  }
  form.addEventListener("submit", function (event) {
    event.preventDefault();
    if (submit.disabled) return;
    submit.disabled = true;
    message.textContent = "Sending to your TV…";
    fetch("/sign-in", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        token: token,
        username: document.getElementById("username").value,
        password: document.getElementById("password").value,
      }),
    })
      .then(function (res) {
        return res.json().then(function (result) {
          if (!res.ok) throw Error(result.error || "Please try again.");
          return result;
        });
      })
      .then(function () {
        form.hidden = true;
        document.getElementById("password").value = "";
        message.textContent =
          "Details sent. Your TV is signing in. You can close this page.";
      })
      .catch(function (error) {
        submit.disabled = false;
        message.textContent =
          error.message ||
          "Unable to reach your TV. Keep both devices on the same Wi-Fi.";
      });
  });
})();
