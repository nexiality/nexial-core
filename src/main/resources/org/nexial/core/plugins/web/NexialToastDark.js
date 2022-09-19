if (!window.nexial) { window.nexial = {}; }
window.nexial.ToastDark = (msg, duration) => {
	let toastId = '_nexial_toast_';
	let toastCount = 0;
	let newToastId = -1;
	let newToastBottom = '10px';

	while (newToastId === -1) {
		let oldToaster = document.getElementById(toastId + toastCount);
		if (oldToaster) {
			toastCount++;
		} else {
			newToastId = toastCount;
			newToastBottom = '' + (newToastId * 40 + 10) + 'px';
			break;
		}
	}

	let el = document.createElement('div');
	el.setAttribute('style', 'position: fixed; z-index: 999; bottom: ' + newToastBottom + ';' +
													 'right: 10px; padding: 5px 12px; max-width: 800px;' +
													 'font-size: 13pt;text-align: center; white-space: nowrap;' +
													 'background-color: rgba(20, 20, 20, 0.85);' +
													 'color: rgba(240, 240, 240, 0.95); text-shadow: 1px 1px 2px black;' +
													 'border: 3px solid transparent; border-radius: 5px;' +
													 'box-shadow: 5px 2px 15px 2px rgb(100 100 100 / 50%);' +
													 'transform: scale(0); transition: .6s ease transform');
	el.setAttribute('id', toastId + newToastId);
	el.innerHTML = msg +
								 '<span style="' +
								 'font-size: 8pt; position: relative; display: block; margin: -2px 0 -14px 0;' +
								 'text-align: right; text-shadow: 2px 3px 2px black; color: rgba(240,240,240,0.98);' +
								 '">auto-dismiss in ' + Math.floor(duration / 1000) + 's... </span>';
	setTimeout(function() { el.style.transform = ''; }, 250);
	setTimeout(function() {
		el.style.transform = 'scale(0)';
		setTimeout(function() { el.parentNode.removeChild(el); }, 600);
	}, duration);
	document.body.appendChild(el);
};